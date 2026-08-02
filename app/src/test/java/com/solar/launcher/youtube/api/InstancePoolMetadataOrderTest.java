package com.solar.launcher.youtube.api;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 2026-07-19 — Metadata failover prefers YtApi so Hello → Adele stays reachable.
 */
public class InstancePoolMetadataOrderTest {

    @Test
    public void ytApiAppearsBeforeOtherBackends() {
        InstancePool.clearHostCooldownsForTest();
        List<String> names = new ArrayList<String>();
        names.add("YtApiLegacy");
        names.add("Invidious");
        names.add("Piped");
        if (!InstancePool.metadataOrderPutsYtApiFirstForTest(names)) {
            throw new AssertionError("YtApi should be before Invidious/Piped");
        }
    }

    @Test
    public void ytApiFirstHelperAllowsMissingYtApi() {
        List<String> names = new ArrayList<String>();
        names.add("Invidious");
        names.add("Piped");
        if (!InstancePool.metadataOrderPutsYtApiFirstForTest(names)) {
            throw new AssertionError("no YtApi pool should still pass helper");
        }
    }

    @Test
    public void ytApiAfterOthersFailsHelper() {
        List<String> names = new ArrayList<String>();
        names.add("Invidious");
        names.add("YtApiLegacy");
        if (InstancePool.metadataOrderPutsYtApiFirstForTest(names)) {
            throw new AssertionError("YtApi after Invidious should fail helper");
        }
    }
}
