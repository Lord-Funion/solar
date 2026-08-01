#!/usr/bin/env bash
set -euo pipefail

file="app/src/main/java/dev/lordfunion/rockboxsolar/NativeSoulseekClient.java"
old='private static void close(java.io.Closeable c){if(c!=null)try{c.close();}catch(Exception ignored){}}'
new='private static void close(Socket c){if(c!=null)try{c.close();}catch(Exception ignored){}}'

python3 - "$file" "$old" "$new" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
old = sys.argv[2]
new = sys.argv[3]
text = path.read_text()
if new in text:
    print("API-17 socket fix already present")
elif old in text:
    path.write_text(text.replace(old, new, 1))
    print("Applied API-17 socket compatibility fix")
else:
    raise SystemExit("Expected NativeSoulseekClient close helper was not found")
PY

grep -F "$new" "$file"
