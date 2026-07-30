# Physical Y1 validation gate

This checklist must be completed on each verified Y1 hardware type. A build is not final merely because GitHub Actions compiled it.

## Recovery preparation

1. Identify the player as Type A or Type B from a known-good backup/source, not Android properties alone.
2. Back up preloader, LK, boot, recovery, system, and userdata.
3. Verify ADB authorization and an uninstall/recovery command before selecting Rockbox Solar as HOME.
4. Keep the matching stock firmware and recovery tools available.

## Test procedure

1. Install the debug APK with `adb install -r`.
2. Open **Y1 Hardware Validation**.
3. Select the verified hardware type.
4. Press every wheel direction and physical key; confirm the on-screen key name and scan code.
5. Run storage, audio, Wi-Fi, Bluetooth, microphone/FM, and root tests.
6. Export the JSON report from `/sdcard/RockboxSolar/Diagnostics/`.
7. Exercise each feature for at least one complete operation:
   - local playback and seeking;
   - Wi-Fi connect/reconnect;
   - Bluetooth pair, A2DP connect, suspend/resume, and reconnect;
   - YouTube metadata search;
   - SSH command and SCP retrieval;
   - Deezer preview;
   - stem upload/process/download/mix;
   - Reach direct mode and slskd mode;
   - recording playback;
   - APK update verification;
   - ROM staging without flashing.
8. Reboot five times and confirm HOME, key mapping, playback resume, Wi-Fi, and Bluetooth behavior.
9. Test with internal storage and removable SD storage.
10. Uninstall and recover through ADB.

## Required evidence for final status

- JSON report for Type A and/or Type B actually tested.
- Exact firmware build fingerprint.
- APK SHA-256.
- Pass/fail notes for every test above.
- Any crash log or `adb logcat` excerpt.
- Confirmation that no boot-critical partition was written during APK validation.
