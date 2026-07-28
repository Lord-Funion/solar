package com.solar.launcher.globalcontext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SolarCrashWatchdogTest {

    public static final Map<String, String> props = new HashMap<>();

    private Method originalGetMethod;
    private Method originalSetMethod;

    public static String get(String key, String def) {
        return props.containsKey(key) ? props.get(key) : def;
    }

    public static void set(String key, String val) {
        props.put(key, val);
    }

    @Before
    public void setup() throws Exception {
        props.clear();
        Method getMethod = SolarCrashWatchdogTest.class.getMethod("get", String.class, String.class);
        Method setMethod = SolarCrashWatchdogTest.class.getMethod("set", String.class, String.class);

        Field sGetMethodField = SysPropHelper.class.getDeclaredField("sGetMethod");
        sGetMethodField.setAccessible(true);
        originalGetMethod = (Method) sGetMethodField.get(null);
        sGetMethodField.set(null, getMethod);

        Field sSetMethodField = SysPropHelper.class.getDeclaredField("sSetMethod");
        sSetMethodField.setAccessible(true);
        originalSetMethod = (Method) sSetMethodField.get(null);
        sSetMethodField.set(null, setMethod);
    }

    @After
    public void teardown() throws Exception {
        Field sGetMethodField = SysPropHelper.class.getDeclaredField("sGetMethod");
        sGetMethodField.setAccessible(true);
        sGetMethodField.set(null, originalGetMethod);

        Field sSetMethodField = SysPropHelper.class.getDeclaredField("sSetMethod");
        sSetMethodField.setAccessible(true);
        sSetMethodField.set(null, originalSetMethod);
    }

    @Test
    public void testApplyEmergencyFromStreak_BelowThreshold() throws Exception {
        SolarCrashWatchdog watchdog = new SolarCrashWatchdog();
        Method applyMethod = SolarCrashWatchdog.class.getDeclaredMethod("applyEmergencyFromStreak");
        applyMethod.setAccessible(true);

        SysPropHelper.set(SolarCrashWatchdog.PROP_CRASH_STREAK, String.valueOf(SolarCrashWatchdog.STREAK_THRESHOLD - 1));
        applyMethod.invoke(watchdog);
        assertNull(props.get(SolarCrashWatchdog.PROP_EMERGENCY_MODE));
    }

    @Test
    public void testApplyEmergencyFromStreak_AtThreshold() throws Exception {
        SolarCrashWatchdog watchdog = new SolarCrashWatchdog();
        Method applyMethod = SolarCrashWatchdog.class.getDeclaredMethod("applyEmergencyFromStreak");
        applyMethod.setAccessible(true);

        SysPropHelper.set(SolarCrashWatchdog.PROP_CRASH_STREAK, String.valueOf(SolarCrashWatchdog.STREAK_THRESHOLD));
        applyMethod.invoke(watchdog);
        assertEquals("1", props.get(SolarCrashWatchdog.PROP_EMERGENCY_MODE));
    }

    @Test
    public void testApplyEmergencyFromStreak_AboveThreshold() throws Exception {
        SolarCrashWatchdog watchdog = new SolarCrashWatchdog();
        Method applyMethod = SolarCrashWatchdog.class.getDeclaredMethod("applyEmergencyFromStreak");
        applyMethod.setAccessible(true);

        SysPropHelper.set(SolarCrashWatchdog.PROP_CRASH_STREAK, String.valueOf(SolarCrashWatchdog.STREAK_THRESHOLD + 1));
        applyMethod.invoke(watchdog);
        assertEquals("1", props.get(SolarCrashWatchdog.PROP_EMERGENCY_MODE));
    }

    @Test
    public void testApplyEmergencyFromStreak_InvalidValue() throws Exception {
        SolarCrashWatchdog watchdog = new SolarCrashWatchdog();
        Method applyMethod = SolarCrashWatchdog.class.getDeclaredMethod("applyEmergencyFromStreak");
        applyMethod.setAccessible(true);

        SysPropHelper.set(SolarCrashWatchdog.PROP_CRASH_STREAK, "invalid");
        applyMethod.invoke(watchdog);
        assertNull(props.get(SolarCrashWatchdog.PROP_EMERGENCY_MODE));
    }
}
