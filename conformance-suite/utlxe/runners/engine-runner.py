#!/usr/bin/env python3
"""
UTLXe Engine Conformance Suite Runner

Spawns UTLXe and runs transformation tests. Supports all transport modes:
  - stdio-proto (default): varint-delimited protobuf over stdin/stdout
  - stdio-json: line-delimited JSON over stdin/stdout (requires --bundle)

Usage:
    python3 engine-runner.py [CATEGORY] [TEST_NAME] [OPTIONS]
    python3 engine-runner.py                             # Run all tests
    python3 engine-runner.py throughput                   # Run throughput tests
    python3 engine-runner.py single-input identity_json   # Run specific test
    python3 engine-runner.py -v --workers 4               # Verbose, 4 workers
    python3 engine-runner.py --mode stdio-json --bundle /path/to/bundle  # stdio-json mode

Requires:
    - Java 17+ on PATH
    - UTLXe JAR: set UTLXE_JAR_PATH or place at default build location
    - pip install pyyaml
"""

import argparse
import json
import math
import os
import random
import struct
import subprocess
import sys
import threading
import time
import yaml
from pathlib import Path
from collections import defaultdict

# Add generated proto path
SCRIPT_DIR = Path(__file__).parent
SUITE_DIR = SCRIPT_DIR.parent
REPO_ROOT = SUITE_DIR.parent.parent
TESTS_DIR = SUITE_DIR / "tests"

# Proto imports — generated from utlxe.proto
# We use raw protobuf wire format to avoid requiring generated stubs.
# Instead, we build the messages manually using the protobuf library.
try:
    from google.protobuf import descriptor_pb2
    from google.protobuf import descriptor_pool
    from google.protobuf import symbol_database
    HAS_PROTOBUF = True
except ImportError:
    HAS_PROTOBUF = False


class VarintCodec:
    """Varint encode/decode for protobuf delimited framing."""

    @staticmethod
    def encode_varint(value):
        """Encode integer as base-128 varint bytes."""
        result = bytearray()
        while value >= 0x80:
            result.append((value & 0x7F) | 0x80)
            value >>= 7
        result.append(value & 0x7F)
        return bytes(result)

    @staticmethod
    def decode_varint(stream):
        """Decode varint from stream. Returns (value, bytes_read) or (-1, 0) on EOF."""
        result = 0
        shift = 0
        for i in range(5):
            b = stream.read(1)
            if not b:
                if i == 0:
                    return -1, 0  # clean EOF
                raise IOError("Unexpected EOF in varint")
            byte = b[0]
            result |= (byte & 0x7F) << shift
            if (byte & 0x80) == 0:
                return result, i + 1
            shift += 7
        raise IOError("Varint too long")


class StdioProtoClient:
    """
    Communicates with UTLXe via varint-delimited protobuf over stdin/stdout.

    Uses raw protobuf encoding without generated stubs — builds messages
    manually from field numbers and wire types. This keeps the runner
    dependency-free (only needs the protobuf library, not generated code).
    """

    # MessageType enum values (from utlxe.proto)
    LOAD_TRANSFORMATION_REQUEST = 1
    EXECUTE_REQUEST = 2
    EXECUTE_BATCH_REQUEST = 3
    UNLOAD_TRANSFORMATION_REQUEST = 4
    HEALTH_REQUEST = 5
    EXECUTE_PIPELINE_REQUEST = 6
    LOAD_TRANSFORMATION_RESPONSE = 11
    EXECUTE_RESPONSE = 12
    EXECUTE_BATCH_RESPONSE = 13
    UNLOAD_TRANSFORMATION_RESPONSE = 14
    HEALTH_RESPONSE = 15
    EXECUTE_PIPELINE_RESPONSE = 16

    def __init__(self, jar_path, workers=1, verbose=False):
        self.jar_path = jar_path
        self.workers = workers
        self.verbose = verbose
        self.process = None

    def start(self):
        """Start UTLXe subprocess in stdio-proto mode."""
        java = self._find_java()
        cmd = [java, "-jar", str(self.jar_path), "--mode", "stdio-proto", "--workers", str(self.workers)]
        if self.verbose:
            print(f"  Starting: {' '.join(cmd)}")
        self.process = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE
        )

    def stop(self):
        """Stop UTLXe subprocess."""
        if self.process:
            self.process.stdin.close()
            self.process.wait(timeout=10)
            self.process = None

    def _find_java(self):
        java_home = os.environ.get("JAVA_HOME")
        if java_home:
            java = os.path.join(java_home, "bin", "java")
            if os.path.exists(java):
                return java
        return "java"

    def _send_envelope(self, msg_type, payload_bytes):
        """Send a StdioEnvelope (varint-delimited)."""
        # Build StdioEnvelope: field 1 = type (varint), field 2 = payload (bytes)
        envelope = b""
        # Field 1: type (field_number=1, wire_type=0 varint) = tag 0x08
        envelope += b"\x08" + VarintCodec.encode_varint(msg_type)
        # Field 2: payload (field_number=2, wire_type=2 length-delimited) = tag 0x12
        envelope += b"\x12" + VarintCodec.encode_varint(len(payload_bytes)) + payload_bytes

        # Write varint-delimited envelope
        self.process.stdin.write(VarintCodec.encode_varint(len(envelope)))
        self.process.stdin.write(envelope)
        self.process.stdin.flush()

    def _recv_envelope(self):
        """Read a StdioEnvelope (varint-delimited). Returns (type, payload_bytes)."""
        length, _ = VarintCodec.decode_varint(self.process.stdout)
        if length < 0:
            raise IOError("EOF from UTLXe")
        data = self.process.stdout.read(length)
        if len(data) < length:
            raise IOError(f"Short read: expected {length}, got {len(data)}")
        return self._parse_envelope(data)

    def _parse_envelope(self, data):
        """Parse StdioEnvelope fields manually."""
        msg_type = 0
        payload = b""
        pos = 0
        while pos < len(data):
            tag = data[pos]
            pos += 1
            field_number = tag >> 3
            wire_type = tag & 0x07

            if wire_type == 0:  # varint
                value = 0
                shift = 0
                while pos < len(data):
                    b = data[pos]
                    pos += 1
                    value |= (b & 0x7F) << shift
                    if (b & 0x80) == 0:
                        break
                    shift += 7
                if field_number == 1:
                    msg_type = value
            elif wire_type == 2:  # length-delimited
                length = 0
                shift = 0
                while pos < len(data):
                    b = data[pos]
                    pos += 1
                    length |= (b & 0x7F) << shift
                    if (b & 0x80) == 0:
                        break
                    shift += 7
                field_data = data[pos:pos + length]
                pos += length
                if field_number == 2:
                    payload = field_data

        return msg_type, payload

    def _parse_response_fields(self, data):
        """Parse protobuf message fields into a dict of {field_number: value}."""
        fields = {}
        pos = 0
        while pos < len(data):
            tag = data[pos]
            pos += 1
            field_number = tag >> 3
            wire_type = tag & 0x07

            if wire_type == 0:  # varint
                value = 0
                shift = 0
                while pos < len(data):
                    b = data[pos]
                    pos += 1
                    value |= (b & 0x7F) << shift
                    if (b & 0x80) == 0:
                        break
                    shift += 7
                fields[field_number] = value
            elif wire_type == 2:  # length-delimited
                length = 0
                shift = 0
                while pos < len(data):
                    b = data[pos]
                    pos += 1
                    length |= (b & 0x7F) << shift
                    if (b & 0x80) == 0:
                        break
                    shift += 7
                fields[field_number] = data[pos:pos + length]
                pos += length
            elif wire_type == 5:  # 32-bit fixed
                fields[field_number] = data[pos:pos + 4]
                pos += 4
            elif wire_type == 1:  # 64-bit fixed
                fields[field_number] = data[pos:pos + 8]
                pos += 8

        return fields

    def _build_string_field(self, field_number, value):
        """Build a protobuf string field."""
        encoded = value.encode("utf-8")
        tag = (field_number << 3) | 2
        return VarintCodec.encode_varint(tag) + VarintCodec.encode_varint(len(encoded)) + encoded

    def _build_bytes_field(self, field_number, value):
        """Build a protobuf bytes field."""
        tag = (field_number << 3) | 2
        return VarintCodec.encode_varint(tag) + VarintCodec.encode_varint(len(value)) + value

    def _build_varint_field(self, field_number, value):
        """Build a protobuf varint field."""
        tag = (field_number << 3) | 0
        return VarintCodec.encode_varint(tag) + VarintCodec.encode_varint(value)

    def load_transformation(self, transform_id, utlx_source, strategy="TEMPLATE"):
        """Send LoadTransformationRequest, return (success, error, metrics_us)."""
        # Build LoadTransformationRequest
        payload = b""
        payload += self._build_string_field(1, transform_id)
        payload += self._build_string_field(2, utlx_source)
        payload += self._build_string_field(3, strategy)

        self._send_envelope(self.LOAD_TRANSFORMATION_REQUEST, payload)
        resp_type, resp_data = self._recv_envelope()

        fields = self._parse_response_fields(resp_data)
        success = fields.get(1, 0)  # bool as varint
        error = fields.get(2, b"").decode("utf-8") if 2 in fields else ""
        return bool(success), error

    def execute(self, transform_id, payload_data, content_type="application/json", correlation_id=""):
        """Send ExecuteRequest, return (success, output, error, correlation_id)."""
        req = b""
        req += self._build_string_field(1, transform_id)
        req += self._build_bytes_field(2, payload_data.encode("utf-8") if isinstance(payload_data, str) else payload_data)
        req += self._build_string_field(3, content_type)
        if correlation_id:
            req += self._build_string_field(5, correlation_id)

        self._send_envelope(self.EXECUTE_REQUEST, req)
        resp_type, resp_data = self._recv_envelope()

        fields = self._parse_response_fields(resp_data)
        success = bool(fields.get(1, 0))
        output = fields.get(2, b"").decode("utf-8") if 2 in fields else ""
        error = fields.get(4, b"").decode("utf-8") if 4 in fields else ""
        corr = fields.get(9, b"").decode("utf-8") if 9 in fields else ""
        return success, output, error, corr

    def execute_batch(self, transform_id, items):
        """Send ExecuteBatchRequest, return list of (success, output, error, correlation_id)."""
        # Build batch items
        batch_items_bytes = b""
        for item_data, content_type, corr_id in items:
            item = b""
            item += self._build_bytes_field(1, item_data.encode("utf-8") if isinstance(item_data, str) else item_data)
            item += self._build_string_field(2, content_type)
            item += self._build_string_field(4, corr_id)
            # Field 2 of BatchRequest = repeated BatchItem (field_number=2)
            batch_items_bytes += self._build_bytes_field(2, item)

        req = self._build_string_field(1, transform_id) + batch_items_bytes

        self._send_envelope(self.EXECUTE_BATCH_REQUEST, req)
        resp_type, resp_data = self._recv_envelope()

        # Parse ExecuteBatchResponse — field 1 = repeated ExecuteResponse
        results = []
        fields = self._parse_response_fields(resp_data)
        # Field 1 is repeated — we need to parse all occurrences
        pos = 0
        while pos < len(resp_data):
            tag = resp_data[pos]
            pos += 1
            field_number = tag >> 3
            wire_type = tag & 0x07
            if wire_type == 2:
                length = 0
                shift = 0
                while pos < len(resp_data):
                    b = resp_data[pos]
                    pos += 1
                    length |= (b & 0x7F) << shift
                    if (b & 0x80) == 0:
                        break
                    shift += 7
                field_data = resp_data[pos:pos + length]
                pos += length
                if field_number == 1:  # repeated ExecuteResponse
                    r_fields = self._parse_response_fields(field_data)
                    results.append((
                        bool(r_fields.get(1, 0)),
                        r_fields.get(2, b"").decode("utf-8") if 2 in r_fields else "",
                        r_fields.get(4, b"").decode("utf-8") if 4 in r_fields else "",
                        r_fields.get(9, b"").decode("utf-8") if 9 in r_fields else ""
                    ))
            elif wire_type == 0:
                while pos < len(resp_data) and (resp_data[pos] & 0x80):
                    pos += 1
                pos += 1

        return results

    def health(self):
        """Send HealthRequest, return (state, uptime_ms, loaded, executions, errors)."""
        self._send_envelope(self.HEALTH_REQUEST, b"")
        resp_type, resp_data = self._recv_envelope()
        fields = self._parse_response_fields(resp_data)
        state = fields.get(1, b"").decode("utf-8") if 1 in fields else ""
        uptime = fields.get(2, 0)
        loaded = fields.get(3, 0)
        executions = fields.get(4, 0)
        errors = fields.get(5, 0)
        return state, uptime, loaded, executions, errors

    def execute_pipeline(self, transformation_ids, payload_data, content_type="application/json", correlation_id=""):
        """Send ExecutePipelineRequest, return (success, output, error, stages_completed, duration_us)."""
        req = b""
        for tid in transformation_ids:
            req += self._build_string_field(1, tid)
        req += self._build_bytes_field(2, payload_data.encode("utf-8") if isinstance(payload_data, str) else payload_data)
        req += self._build_string_field(3, content_type)
        if correlation_id:
            req += self._build_string_field(4, correlation_id)

        self._send_envelope(self.EXECUTE_PIPELINE_REQUEST, req)
        resp_type, resp_data = self._recv_envelope()

        fields = self._parse_response_fields(resp_data)
        success = bool(fields.get(1, 0))
        output = fields.get(2, b"").decode("utf-8") if 2 in fields else ""
        error = fields.get(4, b"").decode("utf-8") if 4 in fields else ""
        stages = fields.get(9, 0)
        duration = fields.get(10, 0)
        return success, output, error, stages, duration

    def execute_concurrent_burst(self, transform_id, payloads, content_type="application/json"):
        """
        Fire N execute requests concurrently using writer + reader threads.
        Returns list of (success, output, error, correlation_id, duration_ms).

        Architecture:
        - Writer thread: fires all requests as fast as possible (serialized writes to stdin)
        - Reader thread: reads all responses, matches by correlation ID
        - Main thread: waits for both to complete, collects results
        """
        count = len(payloads)
        results = {}  # corr_id -> (success, output, error, corr_id, duration_ms)
        send_times = {}  # corr_id -> time.perf_counter()
        write_lock = threading.Lock()
        reader_done = threading.Event()
        writer_done = threading.Event()
        errors_list = []

        def writer():
            """Fire all requests as fast as possible."""
            try:
                for i, payload in enumerate(payloads):
                    corr_id = f"conc-{i+1}"
                    req = b""
                    req += self._build_string_field(1, transform_id)
                    req += self._build_bytes_field(2, payload.encode("utf-8") if isinstance(payload, str) else payload)
                    req += self._build_string_field(3, content_type)
                    req += self._build_string_field(5, corr_id)

                    send_times[corr_id] = time.perf_counter()
                    with write_lock:
                        self._send_envelope(self.EXECUTE_REQUEST, req)
            except Exception as e:
                errors_list.append(f"Writer error: {e}")
            finally:
                writer_done.set()

        def reader():
            """Read all responses and match by correlation ID."""
            try:
                received = 0
                while received < count:
                    resp_type, resp_data = self._recv_envelope()
                    recv_time = time.perf_counter()
                    fields = self._parse_response_fields(resp_data)

                    success = bool(fields.get(1, 0))
                    output = fields.get(2, b"").decode("utf-8") if 2 in fields else ""
                    error = fields.get(4, b"").decode("utf-8") if 4 in fields else ""
                    corr_id = fields.get(9, b"").decode("utf-8") if 9 in fields else ""

                    duration_ms = 0
                    if corr_id in send_times:
                        duration_ms = (recv_time - send_times[corr_id]) * 1000

                    results[corr_id] = (success, output, error, corr_id, duration_ms)
                    received += 1
            except Exception as e:
                errors_list.append(f"Reader error: {e}")
            finally:
                reader_done.set()

        # Launch both threads
        wall_start = time.perf_counter()
        writer_thread = threading.Thread(target=writer, daemon=True)
        reader_thread = threading.Thread(target=reader, daemon=True)
        reader_thread.start()
        writer_thread.start()

        # Wait for completion
        writer_thread.join(timeout=60)
        reader_thread.join(timeout=60)
        wall_ms = (time.perf_counter() - wall_start) * 1000

        if errors_list:
            return None, errors_list, wall_ms

        # Return results ordered by correlation ID
        ordered = []
        for i in range(count):
            corr_id = f"conc-{i+1}"
            if corr_id in results:
                ordered.append(results[corr_id])
            else:
                ordered.append((False, "", f"Missing response for {corr_id}", corr_id, 0))

        return ordered, errors_list, wall_ms


class StdioJsonClient:
    """
    Communicates with UTLXe via line-delimited JSON over stdin/stdout.
    Used for testing the stdio-json transport mode (backward-compatible, bundle-based).
    """

    def __init__(self, jar_path, bundle_path, verbose=False):
        self.jar_path = jar_path
        self.bundle_path = bundle_path
        self.verbose = verbose
        self.process = None

    def start(self):
        java = self._find_java()
        cmd = [java, "-jar", str(self.jar_path), "--bundle", str(self.bundle_path)]
        if self.verbose:
            print(f"  Starting (stdio-json): {' '.join(cmd)}")
        self.process = subprocess.Popen(
            cmd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )

    def stop(self):
        if self.process:
            self.process.stdin.close()
            try:
                self.process.wait(timeout=10)
            except Exception:
                self.process.kill()
            self.process = None

    def _find_java(self):
        java_home = os.environ.get("JAVA_HOME")
        if java_home:
            java = os.path.join(java_home, "bin", "java")
            if os.path.exists(java):
                return java
        return "java"

    def execute_json(self, payload_str):
        """Send a JSON line, read a JSON line back. Returns (output_str, duration_ms)."""
        start = time.perf_counter()
        self.process.stdin.write(payload_str.strip() + "\n")
        self.process.stdin.flush()
        response_line = self.process.stdout.readline()
        duration_ms = (time.perf_counter() - start) * 1000
        return response_line.strip(), duration_ms


# =========================================================================
# Throughput metrics
# =========================================================================

def percentile(sorted_list, pct):
    """Calculate percentile from a sorted list."""
    if not sorted_list:
        return 0
    k = (len(sorted_list) - 1) * (pct / 100.0)
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return sorted_list[int(k)]
    return sorted_list[int(f)] * (c - k) + sorted_list[int(c)] * (k - f)


def generate_burst_payload(template, index, test_data):
    """Generate a single payload from a template with {{INDEX}} and other placeholders."""
    payload = template.replace("{{INDEX}}", str(index))

    # Age range for person-type tests
    age_range = test_data.get("burst", {}).get("age_range", [18, 65])
    age = random.randint(age_range[0], age_range[1])
    payload = payload.replace("{{AGE}}", str(age))

    # Quantity range
    qty_range = test_data.get("burst", {}).get("qty_range", [1, 10])
    qty = random.randint(qty_range[0], qty_range[1])
    payload = payload.replace("{{QTY}}", str(qty))

    return payload


def run_throughput_test(client, test_data, verbose=False):
    """Run a throughput/burst test. Returns (passed, message, metrics_dict)."""
    name = test_data.get("name", "unknown")
    transformation = test_data.get("transformation", "")
    burst_config = test_data.get("burst", {})
    count = burst_config.get("count", 25)
    mode = burst_config.get("mode", "sequential")  # sequential or batch
    template = burst_config.get("payload_template", '{"index": {{INDEX}}}')
    content_type = burst_config.get("content_type", "application/json")
    limits = test_data.get("throughput_limits", {})

    transform_id = f"throughput-{name}"

    # Load transformation (with strategy from test config)
    strategy = test_data.get("strategy", "TEMPLATE")
    success, error = client.load_transformation(transform_id, transformation, strategy=strategy)
    if not success:
        return False, f"Load failed: {error}", {}

    # Generate payloads
    payloads = [generate_burst_payload(template, i + 1, test_data) for i in range(count)]

    if mode == "batch":
        # ExecuteBatch — single call with all items
        items = [(p, content_type, f"b-{i+1}") for i, p in enumerate(payloads)]

        start = time.perf_counter()
        results = client.execute_batch(transform_id, items)
        total_ms = (time.perf_counter() - start) * 1000

        # Check all succeeded
        errors = [r for r in results if not r[0]]
        if errors:
            return False, f"{len(errors)} items failed in batch: {errors[0][2]}", {}

        avg_ms = total_ms / count
        metrics = {
            "mode": "batch",
            "count": count,
            "total_ms": round(total_ms, 2),
            "avg_ms": round(avg_ms, 2),
            "throughput_msg_s": round(count / (total_ms / 1000), 1) if total_ms > 0 else 0,
        }

    elif mode == "concurrent":
        # Concurrent — fire all requests from writer thread, read responses on reader thread
        results, errs, wall_ms = client.execute_concurrent_burst(
            transform_id, payloads, content_type)

        if errs:
            return False, f"Concurrent burst errors: {'; '.join(errs)}", {}

        if results is None:
            return False, "No results from concurrent burst", {}

        error_count = sum(1 for r in results if not r[0])
        if error_count > 0:
            first_err = next(r for r in results if not r[0])
            return False, f"{error_count}/{count} messages failed: {first_err[2]}", {}

        durations_ms = sorted([r[4] for r in results])
        total_ms = wall_ms  # wall clock for the full burst

        metrics = {
            "mode": f"concurrent (workers={client.workers})",
            "count": count,
            "total_ms": round(total_ms, 2),
            "wall_ms": round(wall_ms, 2),
            "avg_ms": round(sum(durations_ms) / count, 2),
            "min_ms": round(durations_ms[0], 2),
            "max_ms": round(durations_ms[-1], 2),
            "p50_ms": round(percentile(durations_ms, 50), 2),
            "p95_ms": round(percentile(durations_ms, 95), 2),
            "p99_ms": round(percentile(durations_ms, 99), 2),
            "throughput_msg_s": round(count / (wall_ms / 1000), 1) if wall_ms > 0 else 0,
        }

    else:
        # Sequential — individual ExecuteRequests with per-message timing
        durations_ms = []
        error_count = 0

        for i, payload in enumerate(payloads):
            start = time.perf_counter()
            success, output, error, _ = client.execute(
                transform_id, payload, content_type, f"seq-{i+1}")
            elapsed_ms = (time.perf_counter() - start) * 1000
            durations_ms.append(elapsed_ms)

            if not success:
                error_count += 1
                if verbose:
                    print(f"    Message {i+1} failed: {error}")

        if error_count > 0:
            return False, f"{error_count}/{count} messages failed", {}

        durations_ms.sort()
        total_ms = sum(durations_ms)

        metrics = {
            "mode": "sequential",
            "count": count,
            "total_ms": round(total_ms, 2),
            "avg_ms": round(total_ms / count, 2),
            "min_ms": round(durations_ms[0], 2),
            "max_ms": round(durations_ms[-1], 2),
            "p50_ms": round(percentile(durations_ms, 50), 2),
            "p95_ms": round(percentile(durations_ms, 95), 2),
            "p99_ms": round(percentile(durations_ms, 99), 2),
            "throughput_msg_s": round(count / (total_ms / 1000), 1) if total_ms > 0 else 0,
        }

    # Check limits
    limit_violations = []
    max_total = limits.get("max_total_ms")
    if max_total and metrics["total_ms"] > max_total:
        limit_violations.append(f"total {metrics['total_ms']}ms > limit {max_total}ms")

    max_p95 = limits.get("max_p95_ms")
    if max_p95 and "p95_ms" in metrics and metrics["p95_ms"] > max_p95:
        limit_violations.append(f"p95 {metrics['p95_ms']}ms > limit {max_p95}ms")

    if limit_violations:
        return False, f"Throughput limits exceeded: {', '.join(limit_violations)}", metrics

    return True, "Throughput OK", metrics


def format_metrics(metrics):
    """Format metrics dict as a human-readable string."""
    if not metrics:
        return ""
    mode = metrics.get("mode", "?")
    count = metrics.get("count", 0)
    total = metrics.get("total_ms", 0)
    tps = metrics.get("throughput_msg_s", 0)

    parts = [f"{count} msgs in {total}ms ({tps} msg/s)"]
    if "p50_ms" in metrics:
        parts.append(f"p50={metrics['p50_ms']}ms p95={metrics['p95_ms']}ms p99={metrics['p99_ms']}ms")
    if "avg_ms" in metrics:
        parts.append(f"avg={metrics['avg_ms']}ms")
    if "min_ms" in metrics:
        parts.append(f"min={metrics['min_ms']}ms max={metrics['max_ms']}ms")
    return " | ".join(parts)


def compare_json(expected_str, actual_str):
    """Compare two JSON strings structurally. Returns (match, diff_message)."""
    try:
        expected = json.loads(expected_str)
    except json.JSONDecodeError as e:
        return False, f"Expected JSON parse error: {e}"
    try:
        actual = json.loads(actual_str)
    except json.JSONDecodeError as e:
        return False, f"Actual JSON parse error: {e}\nActual output: {actual_str}"

    if expected == actual:
        return True, ""
    return False, f"JSON mismatch:\n  Expected: {json.dumps(expected, indent=2)}\n  Actual:   {json.dumps(actual, indent=2)}"


def compare_output(expected_str, actual_str, fmt):
    """Compare expected vs actual output based on format."""
    expected_str = expected_str.strip()
    actual_str = actual_str.strip()

    if fmt == "json":
        return compare_json(expected_str, actual_str)
    elif fmt == "xml":
        # Normalize whitespace for XML comparison
        import re
        norm_exp = re.sub(r">\s+<", "><", expected_str).strip()
        norm_act = re.sub(r">\s+<", "><", actual_str).strip()
        if norm_exp == norm_act:
            return True, ""
        return False, f"XML mismatch:\n  Expected: {expected_str}\n  Actual:   {actual_str}"
    elif fmt == "csv":
        exp_lines = [l.strip() for l in expected_str.strip().split("\n") if l.strip()]
        act_lines = [l.strip() for l in actual_str.strip().split("\n") if l.strip()]
        if exp_lines == act_lines:
            return True, ""
        return False, f"CSV mismatch:\n  Expected lines: {exp_lines}\n  Actual lines:   {act_lines}"
    elif fmt == "yaml":
        try:
            exp_obj = yaml.safe_load(expected_str)
            act_obj = yaml.safe_load(actual_str)
            if exp_obj == act_obj:
                return True, ""
            return False, f"YAML mismatch:\n  Expected: {exp_obj}\n  Actual:   {act_obj}"
        except Exception as e:
            return False, f"YAML parse error: {e}"
    else:
        if expected_str == actual_str:
            return True, ""
        return False, f"Text mismatch:\n  Expected: {expected_str}\n  Actual:   {actual_str}"


def run_test(client, test_data, verbose=False):
    """Run a single test case. Returns (passed, message) or None for skipped."""
    name = test_data.get("name", "unknown")

    if test_data.get("skip"):
        return None, f"Skipped: {test_data.get('skip_reason', 'no reason')}"

    transformation = test_data.get("transformation", "")
    transform_id = test_data.get("transformation_id", f"test-{name}")

    # ── Throughput/burst test ──
    if "burst" in test_data:
        return run_throughput_test(client, test_data, verbose=verbose)

    # ── Pipeline test ──
    if "pipeline" in test_data:
        pipeline_config = test_data["pipeline"]
        # Load all transformations in the pipeline
        for step in pipeline_config["steps"]:
            step_id = step["id"]
            step_source = step["transformation"]
            step_strategy = step.get("strategy", "TEMPLATE")
            success, error = client.load_transformation(step_id, step_source, strategy=step_strategy)
            if not success:
                return False, f"Pipeline load failed for '{step_id}': {error}"

        # Execute pipeline
        step_ids = [s["id"] for s in pipeline_config["steps"]]
        payload = test_data.get("input", {}).get("data", "{}").strip()
        success, output, error, stages, duration = client.execute_pipeline(step_ids, payload)

        if not success:
            return False, f"Pipeline failed at stage {stages}: {error}"

        # Compare output
        expected_data = test_data["expected"]["data"]
        expected_fmt = test_data["expected"]["format"]
        match, diff = compare_output(expected_data, output, expected_fmt)
        if not match:
            return False, diff

        return True, f"Pipeline OK: {stages} stages in {duration}μs"

    # ── Error test: load should fail ──
    if test_data.get("load_error_expected"):
        success, error = client.load_transformation(transform_id, transformation)
        if not success:
            return True, "Load correctly failed"
        return False, "Expected load to fail but it succeeded"

    # ── Error test: execute against missing transform ──
    if test_data.get("execute_error_expected"):
        success, output, error, _ = client.execute(transform_id, test_data["input"]["data"])
        if not success:
            msg_contains = test_data.get("error_message_contains", "")
            if msg_contains and msg_contains.lower() not in error.lower():
                return False, f"Error message '{error}' does not contain '{msg_contains}'"
            return True, f"Execute correctly failed: {error}"
        return False, "Expected execute to fail but it succeeded"

    # ── Batch test ──
    if "batch_items" in test_data:
        success, error = client.load_transformation(transform_id, transformation)
        if not success:
            return False, f"Load failed: {error}"

        items = []
        for item in test_data["batch_items"]:
            items.append((item["data"], "application/json", item["correlation_id"]))

        results = client.execute_batch(transform_id, items)

        if len(results) != len(test_data["batch_items"]):
            return False, f"Expected {len(test_data['batch_items'])} results, got {len(results)}"

        for i, (item_spec, (r_success, r_output, r_error, r_corr)) in enumerate(zip(test_data["batch_items"], results)):
            if not r_success:
                return False, f"Batch item {i} failed: {r_error}"
            expected_data = item_spec["expected"]["data"]
            expected_fmt = item_spec["expected"]["format"]
            match, diff = compare_output(expected_data, r_output, expected_fmt)
            if not match:
                return False, f"Batch item {i} ({r_corr}): {diff}"

        return True, f"All {len(results)} batch items passed"

    # ── Standard test: load + execute ──
    # Handle single input or multi-input
    if "inputs" in test_data:
        # Multi-input: build JSON envelope
        envelope = {}
        for input_name, input_spec in test_data["inputs"].items():
            input_data = input_spec["data"].strip()
            if input_spec["format"] == "json":
                envelope[input_name] = json.loads(input_data)
            else:
                envelope[input_name] = input_data
        payload = json.dumps(envelope)
    else:
        payload = test_data["input"]["data"].strip()

    # Load transformation
    success, error = client.load_transformation(transform_id, transformation)
    if not success:
        return False, f"Load failed: {error}"

    # Execute
    success, output, error, _ = client.execute(transform_id, payload)
    if not success:
        return False, f"Execute failed: {error}"

    # Compare output
    expected_data = test_data["expected"]["data"]
    expected_fmt = test_data["expected"]["format"]
    match, diff = compare_output(expected_data, output, expected_fmt)
    if not match:
        return False, diff

    return True, "Output matches expected"


def find_tests(tests_dir, category=None, test_name=None):
    """Find all test YAML files, optionally filtered by category and name."""
    tests = []
    for yaml_file in sorted(tests_dir.rglob("*.yaml")):
        if yaml_file.name == "README.md":
            continue
        rel = yaml_file.relative_to(tests_dir)
        cat = str(rel.parent) if str(rel.parent) != "." else ""

        if category and not cat.startswith(category):
            continue

        with open(yaml_file) as f:
            try:
                data = yaml.safe_load(f)
            except Exception as e:
                print(f"  Warning: skipping {yaml_file}: {e}")
                continue

        if data is None:
            continue

        if test_name and data.get("name") != test_name:
            continue

        tests.append((yaml_file, data))
    return tests


def find_jar():
    """Find UTLXe JAR from env or default build location."""
    env = os.environ.get("UTLXE_JAR_PATH")
    if env and os.path.exists(env):
        return env

    # Default build location relative to repo root
    candidates = list(REPO_ROOT.glob("modules/engine/build/libs/utlxe-*.jar"))
    if candidates:
        return str(candidates[0])

    return None


def main():
    parser = argparse.ArgumentParser(description="UTLXe Engine Conformance Suite Runner")
    parser.add_argument("category", nargs="?", help="Test category (e.g., single-input, format-conversion)")
    parser.add_argument("test_name", nargs="?", help="Specific test name")
    parser.add_argument("-v", "--verbose", action="store_true", help="Verbose output")
    parser.add_argument("--jar", help="Path to UTLXe JAR")
    parser.add_argument("--workers", type=int, default=1, help="Worker threads (default: 1)")
    parser.add_argument("--mode", choices=["stdio-proto", "stdio-json"], default="stdio-proto",
                        help="Transport mode (default: stdio-proto)")
    parser.add_argument("--bundle", help="Bundle path (required for stdio-json mode)")
    args = parser.parse_args()

    # Find JAR
    jar_path = args.jar or find_jar()
    if not jar_path:
        print("Error: UTLXe JAR not found.")
        print("  Set UTLXE_JAR_PATH or build with: ./gradlew :modules:engine:jar")
        sys.exit(1)

    # Find tests
    tests = find_tests(TESTS_DIR, args.category, args.test_name)
    if not tests:
        print(f"No tests found{' for ' + args.category if args.category else ''}")
        sys.exit(1)

    print(f"UTLXe Engine Conformance Suite")
    print(f"JAR: {jar_path}")
    print(f"Mode: {args.mode} (workers={args.workers})")
    print(f"Running {len(tests)} test(s)...\n")

    # Start UTLXe
    if args.mode == "stdio-json":
        if not args.bundle:
            print("Error: --bundle required for stdio-json mode")
            sys.exit(1)
        # stdio-json mode: limited to functional tests only (no dynamic loading)
        client = StdioJsonClient(jar_path, args.bundle, verbose=args.verbose)
        try:
            client.start()
            time.sleep(2)
            print(f"Engine ready (stdio-json mode, bundle: {args.bundle})\n")
        except Exception as e:
            print(f"Error starting UTLXe: {e}")
            sys.exit(1)
    else:
        client = StdioProtoClient(jar_path, workers=args.workers, verbose=args.verbose)
        try:
            client.start()
            time.sleep(2)
            state, uptime, _, _, _ = client.health()
            print(f"Engine ready: state={state}, uptime={uptime}ms\n")
        except Exception as e:
            print(f"Error starting UTLXe: {e}")
            sys.exit(1)

    # Run tests
    passed = 0
    failed = 0
    skipped = 0
    failures = []

    for test_file, test_data in tests:
        name = test_data.get("name", test_file.stem)
        category = test_data.get("category", "")

        if args.verbose:
            print(f"Running: {category}/{name}")

        try:
            result = run_test(client, test_data, verbose=args.verbose)
            # run_test returns 2-tuple or 3-tuple (throughput tests add metrics)
            if len(result) == 3:
                success, message, metrics = result
            else:
                success, message = result
                metrics = {}

            if success is None:
                skipped += 1
                print(f"  \u2014 {name} (skipped: {message.replace('Skipped: ', '')})")
            elif success:
                passed += 1
                if metrics:
                    print(f"  \u2713 {name}")
                    print(f"    {format_metrics(metrics)}")
                else:
                    print(f"  \u2713 {name}")
                    if args.verbose:
                        print(f"    {message}")
            else:
                failed += 1
                failures.append((name, message))
                print(f"  \u2717 {name}")
                print(f"    {message}")
                if metrics:
                    print(f"    {format_metrics(metrics)}")
        except Exception as e:
            failed += 1
            failures.append((name, str(e)))
            print(f"  \u2717 {name}")
            print(f"    Exception: {e}")

    # Stop engine
    try:
        client.stop()
    except Exception:
        pass

    # Summary
    total = passed + failed
    print(f"\n{'=' * 50}")
    print(f"Results: {passed}/{total} tests passed" + (f", {skipped} skipped" if skipped else ""))
    if total > 0:
        print(f"Success rate: {passed / total * 100:.0f}%")

    if failures:
        print(f"\n\u2717 {len(failures)} test(s) failed:")
        for name, msg in failures:
            print(f"  - {name}: {msg[:100]}")
        sys.exit(1)
    else:
        print(f"\n\u2713 All tests passed!")
        sys.exit(0)


if __name__ == "__main__":
    main()
