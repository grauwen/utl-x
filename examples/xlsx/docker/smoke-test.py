#!/usr/bin/env python3
"""Verify the Linux UTLXe test deployment without contacting My Bottle Collection."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import io
import json
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from zipfile import ZipFile


XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"


def verify(base_url, rows):
    def post(path, body, content_type="application/json"):
        request = Request(base_url + path, body, {"Content-Type": content_type}, method="POST")
        with urlopen(request, timeout=120) as response:
            return response.headers.get_content_type(), response.read()

    def load(name, source, strategy):
        _, body = post("/api/load", json.dumps({
            "transformationId": name, "utlxSource": source, "strategy": strategy, "maxConcurrent": 2
        }).encode())
        result = json.loads(body)
        assert result["success"], result

    records = [{"Name": f"Réserve 🥃 {index}", "Remaining": 0, "Approximate": False, "Unknown": None}
               for index in range(rows)]
    payload = json.dumps(records, ensure_ascii=False).encode("utf-8")
    for strategy in ("TEMPLATE", "COPY", "COMPILED"):
        writer, reader = "write-" + strategy.lower(), "read-" + strategy.lower()
        load(writer, '%utlx 1.0\ninput json\noutput xlsx {sheet: "Collection"}\n---\n$input', strategy)
        load(reader, '%utlx 1.0\ninput xlsx {sheet: "Collection"}\noutput json\n---\n$input', strategy)
        content_type, workbook = post(f"/api/transformations/{writer}/run", payload)
        assert content_type == XLSX, content_type
        with ZipFile(io.BytesIO(workbook)) as archive:
            assert archive.testzip() is None

        def read_workbook(_):
            content_type, body = post(f"/api/transformations/{reader}/run", workbook, XLSX)
            assert content_type == "application/json", content_type
            assert json.loads(body) == records, "Workbook values changed."

        with ThreadPoolExecutor(max_workers=2) as executor:
            list(executor.map(read_workbook, range(2)))
        try:
            post(f"/api/transformations/{reader}/run", b"invalid workbook", XLSX)
        except HTTPError as error:
            assert error.code == 422, error.code
        else:
            raise AssertionError("Invalid workbook was accepted.")
        print(f"PASS: {strategy}, {rows} rows, XLSX export and two simultaneous imports", flush=True)
    with urlopen(base_url + "/api/health", timeout=10) as response:
        assert response.status == 200
    print("Linux engine smoke checks passed.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:18090")
    parser.add_argument("--rows", type=int, default=250)
    arguments = parser.parse_args()
    if not 1 <= arguments.rows <= 10000:
        parser.error("--rows must be between 1 and 10000")
    verify(arguments.url.rstrip("/"), arguments.rows)
