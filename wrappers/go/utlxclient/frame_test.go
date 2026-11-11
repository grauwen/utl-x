package utlxclient

import (
	"bytes"
	"testing"
)

// WB01 (GitHub issue #6) — a length-delimited frame must reach the pipe as one ordered unit.
//
// The .NET client wrote the length prefix and the payload separately; on Windows the pipe handle is
// opened for overlapped I/O once async writes are used, so the two were unordered and the payload
// could arrive first (08 05 02 instead of 02 08 05). Go never had that hazard, but it had the same
// two-write shape. Both clients now compose the frame once and write it once.

// countingWriter records each individual Write call, so framing can be observed rather than assumed.
type countingWriter struct {
	writes [][]byte
}

func (w *countingWriter) Write(p []byte) (int, error) {
	buf := make([]byte, len(p))
	copy(buf, p)
	w.writes = append(w.writes, buf)
	return len(p), nil
}

func TestAppendFrameIsLengthThenPayload(t *testing.T) {
	// The reported case: a 2-byte payload 08 05 must be framed as 02 08 05.
	got := appendFrame(nil, []byte{0x08, 0x05})
	want := []byte{0x02, 0x08, 0x05}
	if !bytes.Equal(got, want) {
		t.Fatalf("frame = % x, want % x", got, want)
	}
}

func TestFrameIsASingleWrite(t *testing.T) {
	// The property that makes the ordering bug structurally impossible: one Write call, so there
	// are no two operations whose order anything could decide.
	w := &countingWriter{}
	if _, err := w.Write(appendFrame(nil, []byte{0x08, 0x05})); err != nil {
		t.Fatal(err)
	}
	if len(w.writes) != 1 {
		t.Fatalf("frame took %d writes, want 1", len(w.writes))
	}
	if !bytes.Equal(w.writes[0], []byte{0x02, 0x08, 0x05}) {
		t.Fatalf("bytes = % x", w.writes[0])
	}
}

func TestAppendFrameMultiByteLength(t *testing.T) {
	// 300 bytes needs a two-byte varint (ac 02) — where an off-by-one in the length would surface.
	frame := appendFrame(nil, make([]byte, 300))
	if len(frame) != 302 {
		t.Fatalf("len = %d, want 302", len(frame))
	}
	if !bytes.Equal(frame[:2], []byte{0xac, 0x02}) {
		t.Fatalf("prefix = % x, want ac 02", frame[:2])
	}
	// And it must read back with the reader this package already uses.
	n, err := readVarint(bytes.NewReader(frame))
	if err != nil || n != 300 {
		t.Fatalf("readVarint = %d, %v; want 300, nil", n, err)
	}
}

func TestAppendFrameRoundTripsThroughReadVarint(t *testing.T) {
	for _, size := range []int{0, 1, 127, 128, 300, 16384} {
		frame := appendFrame(nil, make([]byte, size))
		n, err := readVarint(bytes.NewReader(frame))
		if err != nil {
			t.Fatalf("size %d: %v", size, err)
		}
		if n != size {
			t.Fatalf("size %d: readVarint = %d", size, n)
		}
	}
}

func TestWriteVarintAgreesWithAppendFrame(t *testing.T) {
	// One encoder, not two: the retained stream helper must produce the same prefix.
	for _, size := range []int{0, 1, 127, 128, 300, 16384} {
		var buf bytes.Buffer
		if err := writeVarint(&buf, size); err != nil {
			t.Fatal(err)
		}
		frame := appendFrame(nil, make([]byte, size))
		if !bytes.Equal(buf.Bytes(), frame[:buf.Len()]) {
			t.Fatalf("size %d: writeVarint % x != frame prefix % x", size, buf.Bytes(), frame[:buf.Len()])
		}
	}
}
