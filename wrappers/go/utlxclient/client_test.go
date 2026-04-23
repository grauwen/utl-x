package utlxclient

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"

	pb "github.com/grauwen/utl-x/wrappers/go/utlxclient/proto/utlxe/v1"
)

const identityUtlx = "%utlx 1.0\ninput json\noutput json\n---\n$input\n"

func findJar() string {
	// 1. Environment variable
	if p := os.Getenv("UTLXE_JAR_PATH"); p != "" {
		return p
	}
	// 2. Relative to repo root
	patterns := []string{
		"../../../modules/engine/build/libs/utlxe-*.jar",
	}
	for _, pattern := range patterns {
		matches, _ := filepath.Glob(pattern)
		if len(matches) > 0 {
			return matches[0]
		}
	}
	return ""
}

func newTestClient(t *testing.T) *Client {
	t.Helper()
	jar := findJar()
	if jar == "" {
		t.Skip("UTLXe JAR not found. Build with: ./gradlew :modules:engine:jar")
	}

	client, err := New(Options{JarPath: jar, Workers: 1})
	if err != nil {
		t.Fatalf("Failed to create client: %v", err)
	}
	t.Cleanup(func() { client.Close() })
	return client
}

func TestHealth(t *testing.T) {
	c := newTestClient(t)

	health, err := c.Health()
	if err != nil {
		t.Fatalf("Health failed: %v", err)
	}
	if health.State == "" {
		t.Error("Expected non-empty state")
	}
	if health.UptimeMs < 0 {
		t.Error("Expected non-negative uptime")
	}
}

func TestLoadTransformation(t *testing.T) {
	c := newTestClient(t)

	resp, err := c.LoadTransformation("test-load", identityUtlx, "TEMPLATE")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}
	if !resp.Success {
		t.Fatalf("Load not successful: %s", resp.Error)
	}
	if resp.Metrics == nil || resp.Metrics.TotalDurationUs <= 0 {
		t.Error("Expected positive compile duration")
	}
}

func TestLoadInvalidSource(t *testing.T) {
	c := newTestClient(t)

	resp, err := c.LoadTransformation("bad", "not valid utlx", "TEMPLATE")
	if err != nil {
		t.Fatalf("Load RPC failed: %v", err)
	}
	if resp.Success {
		t.Error("Expected load to fail for invalid source")
	}
	if resp.Error == "" {
		t.Error("Expected error message")
	}
}

func TestExecute(t *testing.T) {
	c := newTestClient(t)

	_, err := c.LoadTransformation("exec-test", identityUtlx, "TEMPLATE")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	resp, err := c.Execute("exec-test", []byte(`{"name": "Alice", "age": 30}`), "application/json")
	if err != nil {
		t.Fatalf("Execute failed: %v", err)
	}
	if !resp.Success {
		t.Fatalf("Execute not successful: %s", resp.Error)
	}

	output := string(resp.Output)
	if !strings.Contains(output, "Alice") {
		t.Errorf("Output should contain 'Alice': %s", output)
	}
}

func TestExecuteWithStdlib(t *testing.T) {
	c := newTestClient(t)

	utlx := `%utlx 1.0
input json
output json
---
{
  fullName: concat($input.first, " ", $input.last),
  email: lowerCase($input.email)
}
`
	_, err := c.LoadTransformation("stdlib-test", utlx, "TEMPLATE")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	resp, err := c.Execute("stdlib-test", []byte(`{"first": "Marcel", "last": "Grauwen", "email": "MARCEL@TEST.COM"}`), "application/json")
	if err != nil {
		t.Fatalf("Execute failed: %v", err)
	}
	if !resp.Success {
		t.Fatalf("Execute failed: %s", resp.Error)
	}

	output := string(resp.Output)
	if !strings.Contains(output, "Marcel Grauwen") {
		t.Errorf("Expected 'Marcel Grauwen' in output: %s", output)
	}
	if !strings.Contains(output, "marcel@test.com") {
		t.Errorf("Expected 'marcel@test.com' in output: %s", output)
	}
}

func TestExecuteUnknownTransformation(t *testing.T) {
	c := newTestClient(t)

	resp, err := c.Execute("nonexistent", []byte(`{}`), "application/json")
	if err != nil {
		t.Fatalf("Execute RPC failed: %v", err)
	}
	if resp.Success {
		t.Error("Expected execute to fail for unknown transformation")
	}
	if !strings.Contains(resp.Error, "nonexistent") {
		t.Errorf("Error should mention 'nonexistent': %s", resp.Error)
	}
}

func TestExecuteBatch(t *testing.T) {
	c := newTestClient(t)

	utlx := "%utlx 1.0\ninput json\noutput json\n---\n{name: upperCase($input.name)}\n"
	_, err := c.LoadTransformation("batch-test", utlx, "TEMPLATE")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	items := []*pb.BatchItem{
		{Payload: []byte(`{"name": "alice"}`), ContentType: "application/json", CorrelationId: "b-1"},
		{Payload: []byte(`{"name": "bob"}`), ContentType: "application/json", CorrelationId: "b-2"},
		{Payload: []byte(`{"name": "charlie"}`), ContentType: "application/json", CorrelationId: "b-3"},
	}

	resp, err := c.ExecuteBatch("batch-test", items)
	if err != nil {
		t.Fatalf("Batch failed: %v", err)
	}
	if len(resp.Results) != 3 {
		t.Fatalf("Expected 3 results, got %d", len(resp.Results))
	}
	for i, r := range resp.Results {
		if !r.Success {
			t.Errorf("Batch item %d failed: %s", i, r.Error)
		}
	}
	if !strings.Contains(string(resp.Results[0].Output), "ALICE") {
		t.Errorf("First result should contain 'ALICE': %s", string(resp.Results[0].Output))
	}
}

func TestExecutePipeline(t *testing.T) {
	c := newTestClient(t)

	// Stage 1: normalize
	_, err := c.LoadTransformation("pipe-normalize",
		"%utlx 1.0\ninput json\noutput json\n---\n{name: lowerCase($input.NAME), age: $input.AGE}\n", "TEMPLATE")
	if err != nil {
		t.Fatalf("Load stage 1 failed: %v", err)
	}

	// Stage 2: enrich
	_, err = c.LoadTransformation("pipe-enrich",
		"%utlx 1.0\ninput json\noutput json\n---\n{name: $input.name, age: $input.age, isAdult: $input.age >= 18}\n", "TEMPLATE")
	if err != nil {
		t.Fatalf("Load stage 2 failed: %v", err)
	}

	resp, err := c.ExecutePipeline(
		[]string{"pipe-normalize", "pipe-enrich"},
		[]byte(`{"NAME": "ALICE", "AGE": 28}`),
		"application/json", "pipe-1")
	if err != nil {
		t.Fatalf("Pipeline failed: %v", err)
	}
	if !resp.Success {
		t.Fatalf("Pipeline not successful: %s", resp.Error)
	}
	if resp.StagesCompleted != 2 {
		t.Errorf("Expected 2 stages completed, got %d", resp.StagesCompleted)
	}

	output := string(resp.Output)
	if !strings.Contains(output, "alice") {
		t.Errorf("Expected 'alice' in output: %s", output)
	}
	if !strings.Contains(output, "isAdult") {
		t.Errorf("Expected 'isAdult' in output: %s", output)
	}
}

func TestCopyStrategy25Messages(t *testing.T) {
	c := newTestClient(t)

	utlx := `%utlx 1.0
input json
output json
---
{
  fullName: concat($input.firstName, " ", $input.lastName),
  email: lowerCase($input.email),
  isAdult: $input.age >= 18
}
`
	resp, err := c.LoadTransformation("copy-burst", utlx, "COPY")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}
	if !resp.Success {
		t.Fatalf("COPY load failed: %s", resp.Error)
	}

	// Send 25 messages through the COPY strategy
	for i := 1; i <= 25; i++ {
		payload := fmt.Sprintf(`{"firstName": "User", "lastName": "Nr%d", "email": "USER%d@TEST.COM", "age": %d}`, i, i, 15+i)

		result, err := c.Execute("copy-burst", []byte(payload), "application/json")
		if err != nil {
			t.Fatalf("Execute %d failed: %v", i, err)
		}
		if !result.Success {
			t.Fatalf("Execute %d not successful: %s", i, result.Error)
		}

		output := string(result.Output)
		expected := fmt.Sprintf("User Nr%d", i)
		if !strings.Contains(output, expected) {
			t.Errorf("Message %d: expected '%s' in output: %s", i, expected, output)
		}
		expectedEmail := fmt.Sprintf("user%d@test.com", i)
		if !strings.Contains(output, expectedEmail) {
			t.Errorf("Message %d: expected '%s' in output: %s", i, expectedEmail, output)
		}
	}

	// Verify health shows 25 executions
	health, err := c.Health()
	if err != nil {
		t.Fatalf("Health failed: %v", err)
	}
	if health.TotalExecutions < 25 {
		t.Errorf("Expected at least 25 executions, got %d", health.TotalExecutions)
	}
}

func TestUnloadTransformation(t *testing.T) {
	c := newTestClient(t)

	_, err := c.LoadTransformation("to-unload", identityUtlx, "TEMPLATE")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}

	resp, err := c.UnloadTransformation("to-unload")
	if err != nil {
		t.Fatalf("Unload failed: %v", err)
	}
	if !resp.Success {
		t.Error("Unload should succeed")
	}
}

func TestCopyStrategy(t *testing.T) {
	c := newTestClient(t)

	utlx := "%utlx 1.0\ninput json\noutput json\n---\n{name: concat($input.first, \" \", $input.last)}\n"
	resp, err := c.LoadTransformation("copy-go", utlx, "COPY")
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}
	if !resp.Success {
		t.Fatalf("COPY load failed: %s", resp.Error)
	}

	result, err := c.Execute("copy-go", []byte(`{"first": "Marcel", "last": "Grauwen"}`), "application/json")
	if err != nil {
		t.Fatalf("Execute failed: %v", err)
	}
	if !result.Success {
		t.Fatalf("COPY execute failed: %s", result.Error)
	}
	if !strings.Contains(string(result.Output), "Marcel Grauwen") {
		t.Errorf("Expected 'Marcel Grauwen': %s", string(result.Output))
	}
}

func TestFullLifecycle(t *testing.T) {
	c := newTestClient(t)

	// Load
	loadResp, _ := c.LoadTransformation("lifecycle", identityUtlx, "TEMPLATE")
	if !loadResp.Success {
		t.Fatalf("Load failed: %s", loadResp.Error)
	}

	// Execute twice
	for i := 1; i <= 2; i++ {
		resp, _ := c.Execute("lifecycle", []byte(fmt.Sprintf(`{"run": %d}`, i)), "application/json")
		if !resp.Success {
			t.Fatalf("Execute %d failed: %s", i, resp.Error)
		}
	}

	// Health
	health, _ := c.Health()
	if health.LoadedTransformations < 1 {
		t.Error("Expected at least 1 loaded transformation")
	}

	// Unload
	unloadResp, _ := c.UnloadTransformation("lifecycle")
	if !unloadResp.Success {
		t.Error("Unload should succeed")
	}
}
