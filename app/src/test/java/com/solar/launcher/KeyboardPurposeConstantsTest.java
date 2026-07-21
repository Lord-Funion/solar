package com.solar.launcher;

import com.solar.launcher.jellyfin.JellyfinScreenHost;
import com.solar.launcher.jellyfin.JellyfinSettingsHost;
import com.solar.launcher.navidrome.NavidromeScreenHost;
import com.solar.launcher.navidrome.NavidromeSettingsHost;
import com.solar.launcher.plex.PlexScreenHost;
import com.solar.launcher.plex.PlexSettingsHost;
import com.solar.launcher.scrobble.ScrobbleSettingsHost;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * Keyboard purpose ints must be unique across MainActivity and settings hosts.
 * Duplicate broke podcast search (was 4 = SOULSEEK_FIND) and Report Issue
 * (was 19 = Navidrome PASS → "Enter password…" placeholder). 2026-07-20
 */
public class KeyboardPurposeConstantsTest {

    @Test
    public void keyboardPurposeValuesAreUniqueAcrossHosts() throws Exception {
        Map<Integer, String> owner = new HashMap<Integer, String>();
        collectStaticKeyboardInts(MainActivity.class, owner);
        collectStaticKeyboardInts(NavidromeSettingsHost.class, owner);
        collectStaticKeyboardInts(NavidromeScreenHost.class, owner);
        collectStaticKeyboardInts(PlexSettingsHost.class, owner);
        collectStaticKeyboardInts(PlexScreenHost.class, owner);
        collectStaticKeyboardInts(JellyfinSettingsHost.class, owner);
        collectStaticKeyboardInts(JellyfinScreenHost.class, owner);
        collectStaticKeyboardInts(ScrobbleSettingsHost.class, owner);
    }

    /** Record each KEYBOARD_* int; fail when two classes claim the same value. */
    private static void collectStaticKeyboardInts(Class<?> clazz, Map<Integer, String> owner)
            throws Exception {
        for (Field f : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            if (!f.getName().startsWith("KEYBOARD_")) continue;
            if (f.getType() != int.class) continue;
            f.setAccessible(true);
            int v = f.getInt(null);
            String label = clazz.getSimpleName() + "." + f.getName();
            String prev = owner.put(v, label);
            if (prev != null) {
                throw new AssertionError("duplicate KEYBOARD_* value " + v
                        + " at " + label + " (also " + prev + ")");
            }
        }
    }
}
