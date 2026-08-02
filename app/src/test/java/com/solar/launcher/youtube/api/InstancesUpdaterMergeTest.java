package com.solar.launcher.youtube.api;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * 2026-07-19 — Remote HTTPS-only instance lists must keep HTTP seed fallbacks (Y2/A5 TLS drift).
 */
public class InstancesUpdaterMergeTest {

    @Test
    public void mergeKeepsHttpSeedsWhenRemoteIsHttpsOnly() {
        List<String> remote = Arrays.asList(
                "https://inv.example/api",
                "https://piped.example");
        List<String> seeds = Arrays.asList(
                "http://76.82.152.76:3000",
                "http://82.65.13.217:7601");
        List<String> merged = InstancesUpdater.mergeWithSeeds(remote, seeds);
        assertTrue(merged.contains("https://inv.example/api"));
        assertTrue(merged.contains("http://76.82.152.76:3000"));
        assertTrue(merged.contains("http://82.65.13.217:7601"));
    }

    @Test
    public void mergeDoesNotDuplicateSeeds() {
        List<String> remote = new ArrayList<String>(Arrays.asList(
                "http://76.82.152.76:3000",
                "https://other.example"));
        List<String> seeds = Arrays.asList("http://76.82.152.76:3000");
        List<String> merged = InstancesUpdater.mergeWithSeeds(remote, seeds);
        int count = 0;
        for (int i = 0; i < merged.size(); i++) {
            if ("http://76.82.152.76:3000".equals(merged.get(i))) count++;
        }
        assertTrue("expected one seed copy, got " + count, count == 1);
    }
}
