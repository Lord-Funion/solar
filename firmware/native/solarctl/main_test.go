package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestVideoID(t *testing.T) {
	cases := map[string]string{
		"/watch?v=abc123":                     "abc123",
		"https://youtube.com/watch?v=xyz789":   "xyz789",
		"/shorts/shortID":                      "shortID",
		"https://youtube.com/shorts/anotherID": "anotherID",
	}
	for input, want := range cases {
		if got := videoID(input); got != want {
			t.Fatalf("videoID(%q)=%q, want %q", input, got, want)
		}
	}
}

func TestCleanField(t *testing.T) {
	if got := cleanField("a\tb\nc\r"); got != "a b c" {
		t.Fatalf("cleanField returned %q", got)
	}
}

func TestWriteAndReadConfig(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "solar.cfg")
	cfg := defaults()
	cfg["ssh-host"] = "example.test"
	if err := writeConfig(path, cfg); err != nil {
		t.Fatal(err)
	}
	loaded, err := readConfig(path)
	if err != nil {
		t.Fatal(err)
	}
	if loaded["ssh-host"] != "example.test" {
		t.Fatalf("ssh-host=%q", loaded["ssh-host"])
	}
	info, err := os.Stat(path)
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0600 {
		t.Fatalf("config mode=%o", info.Mode().Perm())
	}
}

func TestDeleteDownloadRejectsEscape(t *testing.T) {
	root := t.TempDir()
	outside := filepath.Join(t.TempDir(), "outside.mp3")
	if err := os.WriteFile(outside, []byte("x"), 0644); err != nil {
		t.Fatal(err)
	}
	cfg := defaults()
	cfg["download-dir"] = root
	out := &responseWriter{}
	if err := deleteDownload(cfg, outside, out); err == nil {
		t.Fatal("deleteDownload allowed a path outside download-dir")
	}
	if _, err := os.Stat(outside); err != nil {
		t.Fatalf("outside file was altered: %v", err)
	}
}

func TestStreamPreference(t *testing.T) {
	mp3 := pipedStream{Format: "MP3", Bitrate: 128000}
	opus := pipedStream{Format: "OPUS", Bitrate: 192000}
	if streamScore(mp3) <= streamScore(opus) {
		t.Fatal("MP3 preference should outrank OPUS for direct Rockbox compatibility")
	}
}
