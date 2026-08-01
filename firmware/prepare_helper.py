#!/usr/bin/env python3
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: prepare_helper.py <AndroidManifest.xml>')

path = Path(sys.argv[1])
text = path.read_text(encoding='utf-8')

# The firmware boots Rockbox. Solar is a subordinate system service/app,
# never an Android HOME candidate.
text = re.sub(
    r'\s*<category android:name="android\.intent\.category\.HOME"\s*/>',
    '',
    text,
)

# Rockbox launches these screens explicitly from its native menu using am(1).
activities = [
    'MainActivity',
    'YouTubeActivity',
    'SshActivity',
    'ReachActivity',
    'DeezerActivity',
    'StemActivity',
    'StemMixerActivity',
    'WifiActivity',
    'BluetoothActivity',
    'AudioToolsActivity',
    'PluginsActivity',
    'ThemeImportActivity',
    'UpdateActivity',
    'HardwareTestActivity',
]
for activity in activities:
    pattern = rf'(<activity\s+android:name="\.{re.escape(activity)}"[^>]*android:exported=")false("[^>]*/>)'
    text, count = re.subn(pattern, r'\1true\2', text)
    if count != 1:
        raise SystemExit(f'could not export .{activity}; manifest format changed')

if 'android.intent.category.HOME' in text:
    raise SystemExit('HOME category still present after firmware manifest conversion')

path.write_text(text, encoding='utf-8')
