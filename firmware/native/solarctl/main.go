package main

import (
	"bufio"
	"bytes"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"

	"golang.org/x/crypto/ssh"
	"golang.org/x/crypto/ssh/knownhosts"
)

const (
	defaultConfig = "/sdcard/.rockbox/solar.cfg"
	stateDir      = "/sdcard/.rockbox/solar"
)

type config map[string]string
type request map[string]string

type resultItem struct {
	ID       string `json:"id"`
	Kind     string `json:"kind"`
	Title    string `json:"title"`
	Subtitle string `json:"subtitle"`
	URL      string `json:"url"`
	Extra    any    `json:"extra,omitempty"`
}

type responseWriter struct {
	lines []string
}

func (w *responseWriter) message(s string) { w.lines = append(w.lines, "MESSAGE\t"+cleanField(s)) }
func (w *responseWriter) file(path string) { w.lines = append(w.lines, "FILE\t"+cleanField(path)) }
func (w *responseWriter) item(x resultItem) {
	w.lines = append(w.lines, strings.Join([]string{
		"ITEM", cleanField(x.ID), cleanField(x.Kind), cleanField(x.Title), cleanField(x.Subtitle), cleanField(x.URL),
	}, "\t"))
}
func (w *responseWriter) error(err error) { w.lines = []string{"ERROR\t" + cleanField(err.Error())} }
func (w *responseWriter) write(path string) error {
	if len(w.lines) == 0 {
		w.lines = append(w.lines, "MESSAGE\tDone")
	}
	return atomicWrite(path, []byte(strings.Join(w.lines, "\n")+"\n"), 0644)
}

func main() {
	if len(os.Args) != 3 {
		fmt.Fprintln(os.Stderr, "usage: solarctl REQUEST RESPONSE")
		os.Exit(2)
	}
	reqPath, respPath := os.Args[1], os.Args[2]
	_ = os.MkdirAll(filepath.Dir(respPath), 0755)
	req, err := readKV(reqPath)
	_ = os.Remove(reqPath)
	out := &responseWriter{}
	if err == nil {
		cfg, cfgErr := readConfig(defaultConfig)
		if cfgErr != nil && !errors.Is(cfgErr, os.ErrNotExist) {
			err = cfgErr
		} else {
			err = dispatch(cfg, req, out)
		}
	}
	if err != nil {
		out.error(err)
	}
	if writeErr := out.write(respPath); writeErr != nil {
		fmt.Fprintln(os.Stderr, writeErr)
		os.Exit(1)
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func dispatch(cfg config, req request, out *responseWriter) error {
	switch req["command"] {
	case "config_status":
		return configStatus(cfg, out)
	case "config_set":
		return configSet(cfg, req, out)
	case "wifi_set":
		return wifiSet(req, out)
	case "piped_search":
		return pipedSearch(cfg, req["query"], out)
	case "piped_download":
		return pipedDownload(cfg, req["index"], out)
	case "deezer_search":
		return deezerSearch(cfg, req["query"], out)
	case "deezer_download":
		return deezerDownload(cfg, req["index"], out)
	case "slskd_search":
		return slskdSearch(cfg, req["query"], out)
	case "slskd_queue":
		return slskdQueue(cfg, req["index"], out)
	case "ssh_exec":
		return sshExec(cfg, req["value"], out)
	case "scp_get":
		return scpGet(cfg, req["remote"], req["local"], out)
	case "scp_put":
		return scpPut(cfg, req["local"], req["remote"], out)
	case "stem_minutes":
		return stemMinutes(cfg, out)
	case "stem_split":
		return stemSplit(cfg, req, out)
	case "list_downloads":
		return listDownloads(cfg, out)
	case "delete_download":
		return deleteDownload(cfg, req["path"], out)
	default:
		return fmt.Errorf("unknown command %q", req["command"])
	}
}

func defaults() config {
	return config{
		"piped-instance":               "https://pipedapi.kavin.rocks",
		"deezer-api":                   "https://api.deezer.com",
		"download-dir":                 "/sdcard/Music/RockboxSolar",
		"ca-cert":                      "/data/solar-cacert.pem",
		"slskd-url":                    "",
		"slskd-api-key":                "",
		"ssh-host":                     "",
		"ssh-port":                     "22",
		"ssh-user":                     "",
		"ssh-password":                 "",
		"ssh-key":                      "",
		"ssh-known-hosts":              "/sdcard/.ssh/known_hosts",
		"ssh-insecure-accept-host-key": "false",
		"lalal-key":                    "",
	}
}

func readConfig(path string) (config, error) {
	cfg := defaults()
	data, err := os.ReadFile(path)
	if err != nil {
		return cfg, err
	}
	scanner := bufio.NewScanner(bytes.NewReader(data))
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		i := strings.Index(line, ":")
		if i < 1 {
			continue
		}
		cfg[strings.TrimSpace(line[:i])] = strings.TrimSpace(line[i+1:])
	}
	return cfg, scanner.Err()
}

func readKV(path string) (request, error) {
	f, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer f.Close()
	r := request{}
	scanner := bufio.NewScanner(f)
	scanner.Buffer(make([]byte, 1024), 1024*1024)
	for scanner.Scan() {
		line := scanner.Text()
		if i := strings.IndexByte(line, '='); i > 0 {
			r[line[:i]] = line[i+1:]
		}
	}
	return r, scanner.Err()
}

func configStatus(cfg config, out *responseWriter) error {
	keys := []string{"piped-instance", "deezer-api", "download-dir", "slskd-url", "ssh-host", "ssh-user", "lalal-key"}
	for _, key := range keys {
		value := cfg[key]
		if strings.Contains(key, "key") && value != "" {
			value = "configured"
		}
		if value == "" {
			value = "not configured"
		}
		out.item(resultItem{ID: key, Kind: "config", Title: key, Subtitle: value})
	}
	return nil
}

func configSet(cfg config, req request, out *responseWriter) error {
	key := strings.TrimSpace(req["key"])
	if key == "" || strings.ContainsAny(key, "\r\n:") {
		return errors.New("invalid config key")
	}
	cfg[key] = strings.TrimSpace(req["value"])
	if err := writeConfig(defaultConfig, cfg); err != nil {
		return err
	}
	out.message("Saved " + key)
	return nil
}

func writeConfig(path string, cfg config) error {
	preferred := []string{
		"piped-instance", "deezer-api", "download-dir", "ca-cert", "slskd-url", "slskd-api-key",
		"ssh-host", "ssh-port", "ssh-user", "ssh-password", "ssh-key", "ssh-known-hosts",
		"ssh-insecure-accept-host-key", "lalal-key",
	}
	seen := map[string]bool{}
	var b strings.Builder
	b.WriteString("# Rockbox Solar native configuration\n")
	for _, k := range preferred {
		b.WriteString(k + ": " + strings.ReplaceAll(cfg[k], "\n", " ") + "\n")
		seen[k] = true
	}
	var extra []string
	for k := range cfg {
		if !seen[k] {
			extra = append(extra, k)
		}
	}
	sort.Strings(extra)
	for _, k := range extra {
		b.WriteString(k + ": " + strings.ReplaceAll(cfg[k], "\n", " ") + "\n")
	}
	return atomicWrite(path, []byte(b.String()), 0600)
}

func wifiSet(req request, out *responseWriter) error {
	path := "/sdcard/.rockbox/wifi.cfg"
	lines := []string{}
	if data, err := os.ReadFile(path); err == nil {
		lines = strings.Split(strings.ReplaceAll(string(data), "\r\n", "\n"), "\n")
	}
	values := map[string]string{
		"wifi-ssid":     req["ssid"],
		"wifi-password": req["password"],
	}
	seen := map[string]bool{}
	for i, line := range lines {
		for key, value := range values {
			if strings.HasPrefix(strings.TrimSpace(line), key+":") {
				lines[i] = key + ": " + value
				seen[key] = true
			}
		}
	}
	for key, value := range values {
		if !seen[key] {
			lines = append(lines, key+": "+value)
		}
	}
	if err := atomicWrite(path, []byte(strings.Join(lines, "\n")+"\n"), 0600); err != nil {
		return err
	}
	out.message("Wi-Fi credentials saved")
	return nil
}

func newHTTPClient(cfg config, timeout time.Duration) (*http.Client, error) {
	roots := x509.NewCertPool()
	certPath := cfg["ca-cert"]
	if certPath != "" {
		data, err := os.ReadFile(certPath)
		if err != nil {
			return nil, fmt.Errorf("read CA bundle: %w", err)
		}
		if !roots.AppendCertsFromPEM(data) {
			return nil, errors.New("CA bundle contained no certificates")
		}
	} else if sys, err := x509.SystemCertPool(); err == nil {
		roots = sys
	}
	transport := &http.Transport{
		Proxy:           http.ProxyFromEnvironment,
		TLSClientConfig: &tls.Config{RootCAs: roots, MinVersion: tls.VersionTLS12},
	}
	return &http.Client{Transport: transport, Timeout: timeout}, nil
}

func getJSON(client *http.Client, target string, headers map[string]string, dst any) error {
	req, err := http.NewRequest(http.MethodGet, target, nil)
	if err != nil {
		return err
	}
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	return decodeJSONResponse(resp, dst)
}

func postJSON(client *http.Client, target string, headers map[string]string, payload any, dst any) error {
	data, err := json.Marshal(payload)
	if err != nil {
		return err
	}
	req, err := http.NewRequest(http.MethodPost, target, bytes.NewReader(data))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	return decodeJSONResponse(resp, dst)
}

func decodeJSONResponse(resp *http.Response, dst any) error {
	data, err := io.ReadAll(io.LimitReader(resp.Body, 16*1024*1024))
	if err != nil {
		return err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("HTTP %d: %s", resp.StatusCode, cleanField(string(data)))
	}
	if dst == nil || len(bytes.TrimSpace(data)) == 0 {
		return nil
	}
	if err := json.Unmarshal(data, dst); err != nil {
		return fmt.Errorf("invalid JSON: %w", err)
	}
	return nil
}

func pipedSearch(cfg config, query string, out *responseWriter) error {
	if strings.TrimSpace(query) == "" {
		return errors.New("search text is empty")
	}
	client, err := newHTTPClient(cfg, 45*time.Second)
	if err != nil {
		return err
	}
	endpoint := strings.TrimRight(cfg["piped-instance"], "/") + "/search?q=" + url.QueryEscape(query) + "&filter=videos"
	var raw json.RawMessage
	if err := getJSON(client, endpoint, nil, &raw); err != nil {
		return err
	}
	var envelope struct {
		Items []map[string]any `json:"items"`
	}
	var rows []map[string]any
	if len(raw) > 0 && raw[0] == '[' {
		if err := json.Unmarshal(raw, &rows); err != nil {
			return err
		}
	} else {
		if err := json.Unmarshal(raw, &envelope); err != nil {
			return err
		}
		rows = envelope.Items
	}
	items := make([]resultItem, 0, len(rows))
	for _, row := range rows {
		if stringValue(row["type"]) != "" && stringValue(row["type"]) != "stream" {
			continue
		}
		title := stringValue(row["title"])
		rawURL := stringValue(row["url"])
		id := videoID(rawURL)
		if id == "" {
			id = stringValue(row["id"])
		}
		if title == "" || id == "" {
			continue
		}
		subtitle := stringValue(row["uploaderName"])
		if d := intValue(row["duration"]); d > 0 {
			if subtitle != "" {
				subtitle += " • "
			}
			subtitle += formatDuration(d)
		}
		items = append(items, resultItem{ID: id, Kind: "piped", Title: title, Subtitle: subtitle, URL: rawURL})
		if len(items) >= 50 {
			break
		}
	}
	if len(items) == 0 {
		return errors.New("Piped returned no videos")
	}
	if err := saveCache("piped", items); err != nil {
		return err
	}
	for i, item := range items {
		item.ID = strconv.Itoa(i)
		out.item(item)
	}
	out.message(fmt.Sprintf("%d Piped results", len(items)))
	return nil
}

type pipedStream struct {
	URL      string `json:"url"`
	Format   string `json:"format"`
	MimeType string `json:"mimeType"`
	Codec    string `json:"codec"`
	Bitrate  int    `json:"bitrate"`
}

func pipedDownload(cfg config, index string, out *responseWriter) error {
	item, err := cachedItem("piped", index)
	if err != nil {
		return err
	}
	client, err := newHTTPClient(cfg, 30*time.Minute)
	if err != nil {
		return err
	}
	endpoint := strings.TrimRight(cfg["piped-instance"], "/") + "/streams/" + url.PathEscape(item.ID)
	var payload struct {
		AudioStreams []pipedStream `json:"audioStreams"`
	}
	if err := getJSON(client, endpoint, nil, &payload); err != nil {
		return err
	}
	if len(payload.AudioStreams) == 0 {
		return errors.New("Piped returned no audio streams")
	}
	sort.SliceStable(payload.AudioStreams, func(i, j int) bool {
		return streamScore(payload.AudioStreams[i]) > streamScore(payload.AudioStreams[j])
	})
	stream := payload.AudioStreams[0]
	ext := streamExtension(stream)
	dir := filepath.Join(cfg["download-dir"], "Piped")
	output := uniquePath(dir, sanitizeFilename(item.Title)+ext)
	if err := downloadFile(client, stream.URL, output); err != nil {
		return err
	}
	out.file(output)
	out.message("Downloaded " + filepath.Base(output))
	return nil
}

func streamScore(s pipedStream) int {
	format := strings.ToUpper(s.Format + " " + s.MimeType + " " + s.Codec)
	preference := 0
	switch {
	case strings.Contains(format, "MP3"):
		preference = 4000000
	case strings.Contains(format, "M4A") || strings.Contains(format, "MP4") || strings.Contains(format, "AAC"):
		preference = 3000000
	case strings.Contains(format, "OPUS") || strings.Contains(format, "WEBM"):
		preference = 2000000
	case strings.Contains(format, "OGG"):
		preference = 1000000
	}
	return preference + s.Bitrate
}

func streamExtension(s pipedStream) string {
	format := strings.ToLower(s.Format + " " + s.MimeType + " " + s.Codec)
	switch {
	case strings.Contains(format, "mp3"):
		return ".mp3"
	case strings.Contains(format, "m4a"), strings.Contains(format, "mp4"), strings.Contains(format, "aac"):
		return ".m4a"
	case strings.Contains(format, "opus"):
		return ".opus"
	case strings.Contains(format, "ogg"):
		return ".ogg"
	default:
		return ".webm"
	}
}

func deezerSearch(cfg config, query string, out *responseWriter) error {
	if strings.TrimSpace(query) == "" {
		return errors.New("search text is empty")
	}
	client, err := newHTTPClient(cfg, 45*time.Second)
	if err != nil {
		return err
	}
	endpoint := strings.TrimRight(cfg["deezer-api"], "/") + "/search?q=" + url.QueryEscape(query)
	var payload struct {
		Data []struct {
			ID      json.Number `json:"id"`
			Title   string      `json:"title"`
			Preview string      `json:"preview"`
			Link    string      `json:"link"`
			Artist  struct {
				Name string `json:"name"`
			} `json:"artist"`
			Album struct {
				Title string `json:"title"`
			} `json:"album"`
		} `json:"data"`
		Error any `json:"error"`
	}
	if err := getJSON(client, endpoint, nil, &payload); err != nil {
		return err
	}
	items := make([]resultItem, 0, len(payload.Data))
	for _, row := range payload.Data {
		if row.Title == "" || row.Preview == "" {
			continue
		}
		subtitle := row.Artist.Name
		if row.Album.Title != "" {
			subtitle += " • " + row.Album.Title
		}
		items = append(items, resultItem{ID: row.ID.String(), Kind: "deezer", Title: row.Title, Subtitle: subtitle, URL: row.Preview})
		if len(items) >= 50 {
			break
		}
	}
	if len(items) == 0 {
		return errors.New("Deezer returned no previewable tracks")
	}
	if err := saveCache("deezer", items); err != nil {
		return err
	}
	for i, item := range items {
		item.ID = strconv.Itoa(i)
		out.item(item)
	}
	out.message(fmt.Sprintf("%d Deezer results; downloads are official previews", len(items)))
	return nil
}

func deezerDownload(cfg config, index string, out *responseWriter) error {
	item, err := cachedItem("deezer", index)
	if err != nil {
		return err
	}
	client, err := newHTTPClient(cfg, 10*time.Minute)
	if err != nil {
		return err
	}
	name := item.Title
	if item.Subtitle != "" {
		name += " - " + strings.Split(item.Subtitle, " • ")[0]
	}
	output := uniquePath(filepath.Join(cfg["download-dir"], "Deezer Previews"), sanitizeFilename(name)+".mp3")
	if err := downloadFile(client, item.URL, output); err != nil {
		return err
	}
	out.file(output)
	out.message("Downloaded official Deezer preview")
	return nil
}

func slskdSearch(cfg config, query string, out *responseWriter) error {
	base := strings.TrimRight(cfg["slskd-url"], "/")
	key := cfg["slskd-api-key"]
	if base == "" || key == "" {
		return errors.New("configure slskd-url and slskd-api-key")
	}
	if strings.TrimSpace(query) == "" {
		return errors.New("search text is empty")
	}
	client, err := newHTTPClient(cfg, 30*time.Second)
	if err != nil {
		return err
	}
	id := fmt.Sprintf("solar-%d", time.Now().UnixNano())
	headers := map[string]string{"X-API-Key": key, "Accept": "application/json"}
	body := map[string]any{"id": id, "searchText": query, "fileLimit": 1000, "responseLimit": 100, "searchTimeout": 15000}
	var ignored any
	if err := postJSON(client, base+"/api/v0/searches", headers, body, &ignored); err != nil {
		return err
	}
	time.Sleep(16 * time.Second)
	var raw any
	if err := getJSON(client, base+"/api/v0/searches/"+url.PathEscape(id)+"/responses", headers, &raw); err != nil {
		return err
	}
	responses := asSlice(raw)
	if m, ok := raw.(map[string]any); ok {
		if x := asSlice(m["responses"]); len(x) > 0 {
			responses = x
		} else if x := asSlice(m["data"]); len(x) > 0 {
			responses = x
		}
	}
	items := []resultItem{}
	for _, value := range responses {
		response, ok := value.(map[string]any)
		if !ok {
			continue
		}
		user := stringValue(response["username"])
		queue := intValue(response["queueLength"])
		free := boolValue(response["hasFreeUploadSlot"]) || boolValue(response["slotFree"])
		for _, fileValue := range asSlice(response["files"]) {
			file, ok := fileValue.(map[string]any)
			if !ok {
				continue
			}
			remote := stringValue(file["filename"])
			if remote == "" {
				continue
			}
			size := int64Value(file["size"])
			bitrate := intValue(file["bitRate"])
			if bitrate == 0 {
				bitrate = intValue(file["bitrate"])
			}
			subtitle := fmt.Sprintf("%s • %.1f MiB", user, float64(size)/(1024*1024))
			if bitrate > 0 {
				subtitle += fmt.Sprintf(" • %d kbps", bitrate)
			}
			if free {
				subtitle += " • slot free"
			} else {
				subtitle += fmt.Sprintf(" • queue %d", queue)
			}
			extra := map[string]any{"username": user, "file": file}
			items = append(items, resultItem{Kind: "slskd", Title: filepath.Base(strings.ReplaceAll(remote, "\\", "/")), Subtitle: subtitle, URL: remote, Extra: extra})
			if len(items) >= 100 {
				break
			}
		}
		if len(items) >= 100 {
			break
		}
	}
	if len(items) == 0 {
		return errors.New("slskd returned no files")
	}
	if err := saveCache("slskd", items); err != nil {
		return err
	}
	for i, item := range items {
		item.ID = strconv.Itoa(i)
		out.item(item)
	}
	out.message(fmt.Sprintf("%d Soulseek results through slskd", len(items)))
	return nil
}

func slskdQueue(cfg config, index string, out *responseWriter) error {
	item, err := cachedItem("slskd", index)
	if err != nil {
		return err
	}
	extra, ok := item.Extra.(map[string]any)
	if !ok {
		remarshal, _ := json.Marshal(item.Extra)
		_ = json.Unmarshal(remarshal, &extra)
	}
	user := stringValue(extra["username"])
	file, ok := extra["file"].(map[string]any)
	if !ok || user == "" {
		return errors.New("invalid slskd cache entry")
	}
	client, err := newHTTPClient(cfg, 30*time.Second)
	if err != nil {
		return err
	}
	headers := map[string]string{"X-API-Key": cfg["slskd-api-key"], "Accept": "application/json"}
	endpoint := strings.TrimRight(cfg["slskd-url"], "/") + "/api/v0/transfers/downloads/" + url.PathEscape(user)
	var response any
	if err := postJSON(client, endpoint, headers, []any{file}, &response); err != nil {
		return err
	}
	out.message("Queued in slskd. Retrieve the completed file with SSH/SCP if slskd runs elsewhere.")
	return nil
}

func sshClient(cfg config) (*ssh.Client, error) {
	host := strings.TrimSpace(cfg["ssh-host"])
	userName := strings.TrimSpace(cfg["ssh-user"])
	if host == "" || userName == "" {
		return nil, errors.New("configure ssh-host and ssh-user")
	}
	port := cfg["ssh-port"]
	if port == "" {
		port = "22"
	}
	auth := []ssh.AuthMethod{}
	if keyPath := cfg["ssh-key"]; keyPath != "" {
		data, err := os.ReadFile(keyPath)
		if err != nil {
			return nil, fmt.Errorf("read SSH key: %w", err)
		}
		signer, err := ssh.ParsePrivateKey(data)
		if err != nil {
			return nil, fmt.Errorf("parse SSH key: %w", err)
		}
		auth = append(auth, ssh.PublicKeys(signer))
	}
	if password := cfg["ssh-password"]; password != "" {
		auth = append(auth, ssh.Password(password))
	}
	if len(auth) == 0 {
		return nil, errors.New("configure ssh-key or ssh-password")
	}
	var hostKey ssh.HostKeyCallback
	if strings.EqualFold(cfg["ssh-insecure-accept-host-key"], "true") {
		hostKey = ssh.InsecureIgnoreHostKey()
	} else {
		known := cfg["ssh-known-hosts"]
		if known == "" {
			return nil, errors.New("configure ssh-known-hosts or explicitly enable insecure host-key acceptance")
		}
		callback, err := knownhosts.New(known)
		if err != nil {
			return nil, fmt.Errorf("known_hosts: %w", err)
		}
		hostKey = callback
	}
	clientCfg := &ssh.ClientConfig{User: userName, Auth: auth, HostKeyCallback: hostKey, Timeout: 20 * time.Second}
	return ssh.Dial("tcp", host+":"+port, clientCfg)
}

func sshExec(cfg config, command string, out *responseWriter) error {
	if strings.TrimSpace(command) == "" {
		return errors.New("SSH command is empty")
	}
	client, err := sshClient(cfg)
	if err != nil {
		return err
	}
	defer client.Close()
	session, err := client.NewSession()
	if err != nil {
		return err
	}
	defer session.Close()
	output, err := session.CombinedOutput(command)
	if len(output) > 64*1024 {
		output = output[:64*1024]
	}
	if len(output) > 0 {
		for _, line := range strings.Split(string(output), "\n") {
			if strings.TrimSpace(line) != "" {
				out.message(line)
			}
		}
	}
	if err != nil {
		return fmt.Errorf("SSH command: %w", err)
	}
	if len(output) == 0 {
		out.message("SSH command completed")
	}
	return nil
}

func scpGet(cfg config, remote, local string, out *responseWriter) error {
	if remote == "" {
		return errors.New("remote path is empty")
	}
	if local == "" {
		local = filepath.Join(cfg["download-dir"], "SSH", filepath.Base(remote))
	}
	if err := os.MkdirAll(filepath.Dir(local), 0755); err != nil {
		return err
	}
	client, err := sshClient(cfg)
	if err != nil {
		return err
	}
	defer client.Close()
	session, err := client.NewSession()
	if err != nil {
		return err
	}
	defer session.Close()
	stdout, err := session.StdoutPipe()
	if err != nil {
		return err
	}
	if err := session.Start("cat -- " + shellQuote(remote)); err != nil {
		return err
	}
	part := local + ".part"
	file, err := os.Create(part)
	if err != nil {
		return err
	}
	_, copyErr := io.Copy(file, stdout)
	syncErr := file.Sync()
	closeErr := file.Close()
	waitErr := session.Wait()
	if copyErr != nil {
		return copyErr
	}
	if syncErr != nil {
		return syncErr
	}
	if closeErr != nil {
		return closeErr
	}
	if waitErr != nil {
		return waitErr
	}
	if err := os.Rename(part, local); err != nil {
		return err
	}
	out.file(local)
	out.message("SSH file download complete")
	return nil
}

func scpPut(cfg config, local, remote string, out *responseWriter) error {
	if local == "" || remote == "" {
		return errors.New("local and remote paths are required")
	}
	file, err := os.Open(local)
	if err != nil {
		return err
	}
	defer file.Close()
	client, err := sshClient(cfg)
	if err != nil {
		return err
	}
	defer client.Close()
	session, err := client.NewSession()
	if err != nil {
		return err
	}
	defer session.Close()
	session.Stdin = file
	if err := session.Run("cat > " + shellQuote(remote)); err != nil {
		return err
	}
	out.message("SSH file upload complete")
	return nil
}

func stemMinutes(cfg config, out *responseWriter) error {
	key := cfg["lalal-key"]
	if key == "" {
		return errors.New("configure lalal-key")
	}
	client, err := newHTTPClient(cfg, 2*time.Minute)
	if err != nil {
		return err
	}
	var payload map[string]any
	if err := postJSON(client, "https://www.lalal.ai/api/v1/limits/minutes_left/", map[string]string{"X-License-Key": key}, map[string]any{}, &payload); err != nil {
		return err
	}
	out.message(fmt.Sprintf("LALAL.AI processing minutes left: %v", payload["minutes_left"]))
	return nil
}

func stemSplit(cfg config, req request, out *responseWriter) error {
	key := cfg["lalal-key"]
	source := req["source"]
	stem := req["stem"]
	extraction := req["extraction"]
	if key == "" {
		return errors.New("configure lalal-key")
	}
	if stem == "" {
		stem = "vocals"
	}
	if extraction == "" {
		extraction = "deep_extraction"
	}
	info, err := os.Stat(source)
	if err != nil || !info.Mode().IsRegular() {
		return fmt.Errorf("source file not found: %s", source)
	}
	client, err := newHTTPClient(cfg, 60*time.Minute)
	if err != nil {
		return err
	}
	sourceID, err := lalalUpload(client, key, source)
	if err != nil {
		return err
	}
	taskID, err := lalalStart(client, key, sourceID, stem, extraction)
	if err != nil {
		return err
	}
	tracks, err := lalalWait(client, key, taskID)
	if err != nil {
		return err
	}
	dir := filepath.Join(cfg["download-dir"], "Stems")
	base := strings.TrimSuffix(filepath.Base(source), filepath.Ext(source))
	for _, track := range tracks {
		label := stringValue(track["label"])
		if label == "" {
			label = stringValue(track["type"])
		}
		trackURL := stringValue(track["url"])
		if trackURL == "" {
			continue
		}
		ext := extensionFromURL(trackURL, ".mp3")
		path := uniquePath(dir, sanitizeFilename(base+" - "+label)+ext)
		if err := downloadFile(client, trackURL, path); err != nil {
			return err
		}
		out.file(path)
	}
	_ = lalalDelete(client, key, sourceID)
	if len(out.lines) == 0 {
		return errors.New("LALAL.AI returned no downloadable stems")
	}
	out.message("Stem separation complete")
	return nil
}

func lalalUpload(client *http.Client, key, path string) (string, error) {
	file, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer file.Close()
	req, err := http.NewRequest(http.MethodPost, "https://www.lalal.ai/api/v1/upload/", file)
	if err != nil {
		return "", err
	}
	req.Header.Set("X-License-Key", key)
	req.Header.Set("Content-Type", "application/octet-stream")
	req.Header.Set("Content-Disposition", "attachment; filename=\""+strings.ReplaceAll(filepath.Base(path), "\"", "_")+"\"")
	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	var payload map[string]any
	if err := decodeJSONResponse(resp, &payload); err != nil {
		return "", err
	}
	id := stringValue(payload["id"])
	if id == "" {
		id = stringValue(payload["source_id"])
	}
	if id == "" {
		return "", errors.New("LALAL.AI upload returned no source ID")
	}
	return id, nil
}

func lalalStart(client *http.Client, key, sourceID, stem, extraction string) (string, error) {
	payload := map[string]any{
		"source_id": sourceID,
		"presets": map[string]any{"stem": stem, "splitter": "auto", "extraction_level": extraction},
	}
	var result map[string]any
	if err := postJSON(client, "https://www.lalal.ai/api/v1/split/stem_separator/", map[string]string{"X-License-Key": key}, payload, &result); err != nil {
		return "", err
	}
	task := stringValue(result["task_id"])
	if task == "" {
		task = stringValue(result["id"])
	}
	if nested, ok := result["result"].(map[string]any); ok && task == "" {
		task = stringValue(nested["task_id"])
		if task == "" {
			task = stringValue(nested["id"])
		}
	}
	if task == "" {
		return "", errors.New("LALAL.AI split returned no task ID")
	}
	return task, nil
}

func lalalWait(client *http.Client, key, taskID string) ([]map[string]any, error) {
	for attempt := 0; attempt < 240; attempt++ {
		var checked map[string]any
		if err := postJSON(client, "https://www.lalal.ai/api/v1/check/", map[string]string{"X-License-Key": key}, map[string]any{"task_ids": []string{taskID}}, &checked); err != nil {
			return nil, err
		}
		task := mapAt(checked, "result", taskID)
		if task == nil {
			if direct, ok := checked[taskID].(map[string]any); ok {
				task = direct
			}
		}
		if task != nil {
			status := strings.ToLower(stringValue(task["status"]))
			if status == "error" || status == "failed" {
				return nil, fmt.Errorf("LALAL.AI task failed: %s", firstNonEmpty(stringValue(task["error"]), stringValue(task["message"])))
			}
			if result, ok := task["result"].(map[string]any); ok {
				raw := asSlice(result["tracks"])
				tracks := make([]map[string]any, 0, len(raw))
				for _, value := range raw {
					if track, ok := value.(map[string]any); ok {
						tracks = append(tracks, track)
					}
				}
				if len(tracks) > 0 {
					return tracks, nil
				}
			}
		}
		time.Sleep(5 * time.Second)
	}
	return nil, errors.New("LALAL.AI task timed out")
}

func lalalDelete(client *http.Client, key, sourceID string) error {
	var ignored map[string]any
	return postJSON(client, "https://www.lalal.ai/api/v1/delete/", map[string]string{"X-License-Key": key}, map[string]any{"source_id": sourceID}, &ignored)
}

func listDownloads(cfg config, out *responseWriter) error {
	root := filepath.Clean(cfg["download-dir"])
	items := []resultItem{}
	err := filepath.Walk(root, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil
		}
		if info.IsDir() {
			return nil
		}
		ext := strings.ToLower(filepath.Ext(path))
		switch ext {
		case ".mp3", ".m4a", ".aac", ".opus", ".ogg", ".flac", ".wav", ".webm", ".mp4":
		default:
			return nil
		}
		rel, _ := filepath.Rel(root, path)
		items = append(items, resultItem{Kind: "download", Title: filepath.Base(path), Subtitle: rel, URL: path})
		if len(items) >= 250 {
			return filepath.SkipAll
		}
		return nil
	})
	if err != nil && !errors.Is(err, os.ErrNotExist) {
		return err
	}
	sort.Slice(items, func(i, j int) bool { return items[i].URL < items[j].URL })
	if len(items) == 0 {
		return errors.New("no downloaded audio files found")
	}
	for i, item := range items {
		item.ID = strconv.Itoa(i)
		out.item(item)
	}
	return nil
}

func deleteDownload(cfg config, path string, out *responseWriter) error {
	root, err := filepath.Abs(cfg["download-dir"])
	if err != nil {
		return err
	}
	target, err := filepath.Abs(path)
	if err != nil {
		return err
	}
	rel, err := filepath.Rel(root, target)
	if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(os.PathSeparator)) {
		return errors.New("refusing to delete outside download-dir")
	}
	if err := os.Remove(target); err != nil {
		return err
	}
	out.message("Deleted " + filepath.Base(target))
	return nil
}

func downloadFile(client *http.Client, target, output string) error {
	if err := os.MkdirAll(filepath.Dir(output), 0755); err != nil {
		return err
	}
	part := output + ".part"
	var offset int64
	if info, err := os.Stat(part); err == nil {
		offset = info.Size()
	}
	req, err := http.NewRequest(http.MethodGet, target, nil)
	if err != nil {
		return err
	}
	if offset > 0 {
		req.Header.Set("Range", fmt.Sprintf("bytes=%d-", offset))
	}
	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusPartialContent {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
		return fmt.Errorf("download HTTP %d: %s", resp.StatusCode, cleanField(string(body)))
	}
	flags := os.O_CREATE | os.O_WRONLY
	if resp.StatusCode == http.StatusPartialContent && offset > 0 {
		flags |= os.O_APPEND
	} else {
		flags |= os.O_TRUNC
	}
	file, err := os.OpenFile(part, flags, 0644)
	if err != nil {
		return err
	}
	_, copyErr := io.Copy(file, resp.Body)
	syncErr := file.Sync()
	closeErr := file.Close()
	if copyErr != nil {
		return copyErr
	}
	if syncErr != nil {
		return syncErr
	}
	if closeErr != nil {
		return closeErr
	}
	return os.Rename(part, output)
}

func saveCache(name string, items []resultItem) error {
	if err := os.MkdirAll(stateDir, 0755); err != nil {
		return err
	}
	data, err := json.Marshal(items)
	if err != nil {
		return err
	}
	return atomicWrite(filepath.Join(stateDir, name+"-cache.json"), data, 0600)
}

func cachedItem(name, index string) (resultItem, error) {
	n, err := strconv.Atoi(index)
	if err != nil || n < 0 {
		return resultItem{}, errors.New("invalid result index")
	}
	data, err := os.ReadFile(filepath.Join(stateDir, name+"-cache.json"))
	if err != nil {
		return resultItem{}, err
	}
	var items []resultItem
	if err := json.Unmarshal(data, &items); err != nil {
		return resultItem{}, err
	}
	if n >= len(items) {
		return resultItem{}, errors.New("result index is out of range")
	}
	return items[n], nil
}

func atomicWrite(path string, data []byte, mode os.FileMode) error {
	if err := os.MkdirAll(filepath.Dir(path), 0755); err != nil {
		return err
	}
	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, data, mode); err != nil {
		return err
	}
	if err := os.Chmod(tmp, mode); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

func uniquePath(dir, name string) string {
	_ = os.MkdirAll(dir, 0755)
	path := filepath.Join(dir, name)
	if _, err := os.Stat(path); errors.Is(err, os.ErrNotExist) {
		return path
	}
	ext := filepath.Ext(name)
	base := strings.TrimSuffix(name, ext)
	for i := 2; i < 1000; i++ {
		candidate := filepath.Join(dir, fmt.Sprintf("%s (%d)%s", base, i, ext))
		if _, err := os.Stat(candidate); errors.Is(err, os.ErrNotExist) {
			return candidate
		}
	}
	return filepath.Join(dir, fmt.Sprintf("%d-%s", time.Now().Unix(), name))
}

func sanitizeFilename(s string) string {
	replacer := strings.NewReplacer("/", "_", "\\", "_", ":", "_", "*", "_", "?", "_", "\"", "_", "<", "_", ">", "_", "|", "_")
	s = strings.TrimSpace(replacer.Replace(s))
	if s == "" {
		s = "track"
	}
	if len(s) > 160 {
		s = s[:160]
	}
	return s
}

func cleanField(s string) string {
	s = strings.ReplaceAll(s, "\t", " ")
	s = strings.ReplaceAll(s, "\r", " ")
	s = strings.ReplaceAll(s, "\n", " ")
	if len(s) > 1024 {
		s = s[:1024]
	}
	return strings.TrimSpace(s)
}

func videoID(raw string) string {
	if raw == "" {
		return ""
	}
	if strings.HasPrefix(raw, "/watch?") {
		if u, err := url.Parse(raw); err == nil {
			return u.Query().Get("v")
		}
	}
	if strings.HasPrefix(raw, "/shorts/") {
		return strings.Trim(strings.TrimPrefix(raw, "/shorts/"), "/")
	}
	if u, err := url.Parse(raw); err == nil {
		if id := u.Query().Get("v"); id != "" {
			return id
		}
		if strings.Contains(u.Path, "/shorts/") {
			return strings.Trim(strings.SplitN(u.Path, "/shorts/", 2)[1], "/")
		}
	}
	return ""
}

func formatDuration(seconds int) string {
	if seconds >= 3600 {
		return fmt.Sprintf("%d:%02d:%02d", seconds/3600, (seconds/60)%60, seconds%60)
	}
	return fmt.Sprintf("%d:%02d", seconds/60, seconds%60)
}

func extensionFromURL(raw, fallback string) string {
	if u, err := url.Parse(raw); err == nil {
		ext := strings.ToLower(filepath.Ext(u.Path))
		switch ext {
		case ".mp3", ".m4a", ".aac", ".opus", ".ogg", ".flac", ".wav", ".webm", ".mp4":
			return ext
		}
	}
	return fallback
}

func shellQuote(s string) string { return "'" + strings.ReplaceAll(s, "'", "'\"'\"'") + "'" }
func firstNonEmpty(values ...string) string {
	for _, value := range values {
		if value != "" {
			return value
		}
	}
	return "unknown error"
}
func stringValue(v any) string {
	switch x := v.(type) {
	case string:
		return x
	case json.Number:
		return x.String()
	case float64:
		return strconv.FormatInt(int64(x), 10)
	case int:
		return strconv.Itoa(x)
	case int64:
		return strconv.FormatInt(x, 10)
	default:
		return ""
	}
}
func intValue(v any) int { return int(int64Value(v)) }
func int64Value(v any) int64 {
	switch x := v.(type) {
	case float64:
		return int64(x)
	case int:
		return int64(x)
	case int64:
		return x
	case json.Number:
		n, _ := x.Int64()
		return n
	case string:
		n, _ := strconv.ParseInt(x, 10, 64)
		return n
	default:
		return 0
	}
}
func boolValue(v any) bool {
	switch x := v.(type) {
	case bool:
		return x
	case string:
		b, _ := strconv.ParseBool(x)
		return b
	default:
		return false
	}
}
func asSlice(v any) []any {
	if x, ok := v.([]any); ok {
		return x
	}
	return nil
}
func mapAt(root map[string]any, keys ...string) map[string]any {
	var current any = root
	for _, key := range keys {
		m, ok := current.(map[string]any)
		if !ok {
			return nil
		}
		current = m[key]
	}
	m, _ := current.(map[string]any)
	return m
}
