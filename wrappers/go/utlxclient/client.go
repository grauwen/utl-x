// Package utlxclient provides a Go client for the UTL-X Engine (UTLXe).
//
// It spawns UTLXe as a long-running JVM subprocess and communicates via
// varint-delimited protobuf over stdin/stdout (stdio-proto mode).
//
// Usage:
//
//	client, err := utlxclient.New(utlxclient.Options{JarPath: "/path/to/utlxe.jar"})
//	if err != nil { log.Fatal(err) }
//	defer client.Close()
//
//	err = client.LoadTransformation("my-tx", utlxSource, "TEMPLATE")
//	result, err := client.Execute("my-tx", []byte(`{"name": "Alice"}`), "application/json")
package utlxclient

import (
	"encoding/binary"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strconv"
	"sync"
	"time"

	pb "github.com/grauwen/utl-x/wrappers/go/utlxclient/proto/utlxe/v1"
	"google.golang.org/protobuf/proto"
)

// Options configures the UTLXe client.
type Options struct {
	// JarPath is the path to the utlxe JAR file. Required unless UTLXE_JAR_PATH env is set.
	JarPath string
	// JavaHome is the path to the Java home directory. Falls back to JAVA_HOME env, then "java" on PATH.
	JavaHome string
	// Workers is the number of UTLXe worker threads. Default: 1 (sequential).
	Workers int
	// StartupTimeout is how long to wait for UTLXe to become ready. Default: 30s.
	StartupTimeout time.Duration
}

// Client communicates with a UTLXe subprocess via stdio-proto.
// Thread-safe for concurrent Execute calls.
type Client struct {
	cmd    *exec.Cmd
	stdin  io.WriteCloser
	stdout io.ReadCloser

	writeMu sync.Mutex // serializes writes to stdin
	readMu  sync.Mutex // serializes reads from stdout
}

// New creates and starts a UTLXe client. The JVM subprocess is started immediately.
// Call Close() when done to shut down the subprocess.
func New(opts Options) (*Client, error) {
	jarPath := opts.JarPath
	if jarPath == "" {
		jarPath = os.Getenv("UTLXE_JAR_PATH")
	}
	if jarPath == "" {
		return nil, fmt.Errorf("UTLXe JAR path not set: provide Options.JarPath or set UTLXE_JAR_PATH")
	}

	workers := opts.Workers
	if workers <= 0 {
		workers = 1
	}

	javaExe := resolveJava(opts.JavaHome)

	cmd := exec.Command(javaExe, "-jar", jarPath, "--mode", "stdio-proto", "--workers", strconv.Itoa(workers))
	cmd.Stderr = os.Stderr // UTLXe logs go to stderr

	stdin, err := cmd.StdinPipe()
	if err != nil {
		return nil, fmt.Errorf("stdin pipe: %w", err)
	}
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return nil, fmt.Errorf("stdout pipe: %w", err)
	}

	if err := cmd.Start(); err != nil {
		return nil, fmt.Errorf("start UTLXe: %w", err)
	}

	c := &Client{
		cmd:    cmd,
		stdin:  stdin,
		stdout: stdout,
	}

	// Wait for engine readiness
	timeout := opts.StartupTimeout
	if timeout == 0 {
		timeout = 30 * time.Second
	}
	if err := c.waitReady(timeout); err != nil {
		c.Close()
		return nil, fmt.Errorf("UTLXe not ready: %w", err)
	}

	return c, nil
}

// Close shuts down the UTLXe subprocess gracefully.
func (c *Client) Close() error {
	if c.stdin != nil {
		c.stdin.Close() // EOF signals UTLXe to exit
	}
	if c.cmd != nil && c.cmd.Process != nil {
		done := make(chan error, 1)
		go func() { done <- c.cmd.Wait() }()
		select {
		case <-done:
		case <-time.After(5 * time.Second):
			c.cmd.Process.Kill()
		}
	}
	return nil
}

// LoadTransformation compiles a .utlx source and registers it under the given ID.
// Strategy: "TEMPLATE", "COPY", or "AUTO".
func (c *Client) LoadTransformation(id, utlxSource, strategy string) (*pb.LoadTransformationResponse, error) {
	req := &pb.LoadTransformationRequest{
		TransformationId: id,
		UtlxSource:       utlxSource,
		Strategy:         strategy,
	}
	data, err := proto.Marshal(req)
	if err != nil {
		return nil, err
	}

	respData, err := c.sendAndReceive(pb.MessageType_LOAD_TRANSFORMATION_REQUEST, data)
	if err != nil {
		return nil, err
	}

	resp := &pb.LoadTransformationResponse{}
	if err := proto.Unmarshal(respData, resp); err != nil {
		return nil, err
	}
	return resp, nil
}

// Execute runs a pre-loaded transformation against a payload.
func (c *Client) Execute(transformationID string, payload []byte, contentType string) (*pb.ExecuteResponse, error) {
	return c.ExecuteWithCorrelation(transformationID, payload, contentType, "")
}

// ExecuteWithCorrelation runs a transformation with an explicit correlation ID for response matching.
func (c *Client) ExecuteWithCorrelation(transformationID string, payload []byte, contentType, correlationID string) (*pb.ExecuteResponse, error) {
	req := &pb.ExecuteRequest{
		TransformationId: transformationID,
		Payload:          payload,
		ContentType:      contentType,
		CorrelationId:    correlationID,
	}
	data, err := proto.Marshal(req)
	if err != nil {
		return nil, err
	}

	respData, err := c.sendAndReceive(pb.MessageType_EXECUTE_REQUEST, data)
	if err != nil {
		return nil, err
	}

	resp := &pb.ExecuteResponse{}
	if err := proto.Unmarshal(respData, resp); err != nil {
		return nil, err
	}
	return resp, nil
}

// ExecuteBatch runs a transformation against multiple payloads in one call.
func (c *Client) ExecuteBatch(transformationID string, items []*pb.BatchItem) (*pb.ExecuteBatchResponse, error) {
	req := &pb.ExecuteBatchRequest{
		TransformationId: transformationID,
		Items:            items,
	}
	data, err := proto.Marshal(req)
	if err != nil {
		return nil, err
	}

	respData, err := c.sendAndReceive(pb.MessageType_EXECUTE_BATCH_REQUEST, data)
	if err != nil {
		return nil, err
	}

	resp := &pb.ExecuteBatchResponse{}
	if err := proto.Unmarshal(respData, resp); err != nil {
		return nil, err
	}
	return resp, nil
}

// ExecutePipeline runs a chain of transformations. Output of each stage feeds the next.
func (c *Client) ExecutePipeline(transformationIDs []string, payload []byte, contentType, correlationID string) (*pb.ExecutePipelineResponse, error) {
	req := &pb.ExecutePipelineRequest{
		TransformationIds: transformationIDs,
		Payload:           payload,
		ContentType:       contentType,
		CorrelationId:     correlationID,
	}
	data, err := proto.Marshal(req)
	if err != nil {
		return nil, err
	}

	respData, err := c.sendAndReceive(pb.MessageType_EXECUTE_PIPELINE_REQUEST, data)
	if err != nil {
		return nil, err
	}

	resp := &pb.ExecutePipelineResponse{}
	if err := proto.Unmarshal(respData, resp); err != nil {
		return nil, err
	}
	return resp, nil
}

// UnloadTransformation removes a previously loaded transformation.
func (c *Client) UnloadTransformation(id string) (*pb.UnloadTransformationResponse, error) {
	req := &pb.UnloadTransformationRequest{TransformationId: id}
	data, err := proto.Marshal(req)
	if err != nil {
		return nil, err
	}

	respData, err := c.sendAndReceive(pb.MessageType_UNLOAD_TRANSFORMATION_REQUEST, data)
	if err != nil {
		return nil, err
	}

	resp := &pb.UnloadTransformationResponse{}
	if err := proto.Unmarshal(respData, resp); err != nil {
		return nil, err
	}
	return resp, nil
}

// Health queries engine state and statistics.
func (c *Client) Health() (*pb.HealthResponse, error) {
	req := &pb.HealthRequest{}
	data, err := proto.Marshal(req)
	if err != nil {
		return nil, err
	}

	respData, err := c.sendAndReceive(pb.MessageType_HEALTH_REQUEST, data)
	if err != nil {
		return nil, err
	}

	resp := &pb.HealthResponse{}
	if err := proto.Unmarshal(respData, resp); err != nil {
		return nil, err
	}
	return resp, nil
}

// =========================================================================
// Internal: varint-delimited protobuf over stdin/stdout
// =========================================================================

func (c *Client) sendAndReceive(msgType pb.MessageType, payload []byte) ([]byte, error) {
	envelope := &pb.StdioEnvelope{
		Type:    msgType,
		Payload: payload,
	}
	envData, err := proto.Marshal(envelope)
	if err != nil {
		return nil, fmt.Errorf("marshal envelope: %w", err)
	}

	// Write: varint length + envelope bytes
	c.writeMu.Lock()
	if err := writeVarint(c.stdin, len(envData)); err != nil {
		c.writeMu.Unlock()
		return nil, fmt.Errorf("write varint: %w", err)
	}
	if _, err := c.stdin.Write(envData); err != nil {
		c.writeMu.Unlock()
		return nil, fmt.Errorf("write envelope: %w", err)
	}
	c.writeMu.Unlock()

	// Read: varint length + envelope bytes
	c.readMu.Lock()
	length, err := readVarint(c.stdout)
	if err != nil {
		c.readMu.Unlock()
		return nil, fmt.Errorf("read varint: %w", err)
	}

	respBuf := make([]byte, length)
	if _, err := io.ReadFull(c.stdout, respBuf); err != nil {
		c.readMu.Unlock()
		return nil, fmt.Errorf("read envelope: %w", err)
	}
	c.readMu.Unlock()

	respEnvelope := &pb.StdioEnvelope{}
	if err := proto.Unmarshal(respBuf, respEnvelope); err != nil {
		return nil, fmt.Errorf("unmarshal envelope: %w", err)
	}

	return respEnvelope.Payload, nil
}

func (c *Client) waitReady(timeout time.Duration) error {
	deadline := time.Now().Add(timeout)
	for time.Now().Before(deadline) {
		health, err := c.Health()
		if err == nil && health.State != "" {
			return nil
		}
		time.Sleep(200 * time.Millisecond)
	}
	return fmt.Errorf("timeout after %v", timeout)
}

// writeVarint writes a base-128 varint to the writer.
func writeVarint(w io.Writer, value int) error {
	buf := make([]byte, binary.MaxVarintLen32)
	n := binary.PutUvarint(buf, uint64(value))
	_, err := w.Write(buf[:n])
	return err
}

// readVarint reads a base-128 varint from the reader.
func readVarint(r io.Reader) (int, error) {
	var result uint64
	var shift uint
	for i := 0; i < 5; i++ {
		b := make([]byte, 1)
		_, err := r.Read(b)
		if err != nil {
			return 0, err
		}
		result |= uint64(b[0]&0x7F) << shift
		if b[0]&0x80 == 0 {
			return int(result), nil
		}
		shift += 7
	}
	return 0, fmt.Errorf("varint too long")
}

func resolveJava(javaHome string) string {
	if javaHome != "" {
		path := javaHome + "/bin/java"
		if _, err := os.Stat(path); err == nil {
			return path
		}
	}
	if envHome := os.Getenv("JAVA_HOME"); envHome != "" {
		path := envHome + "/bin/java"
		if _, err := os.Stat(path); err == nil {
			return path
		}
	}
	return "java"
}
