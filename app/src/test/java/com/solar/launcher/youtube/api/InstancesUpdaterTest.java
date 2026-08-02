package com.solar.launcher.youtube.api;

import org.junit.Test;

/**
 * 2026-07-15 — Host JVM: parse notPipe.json shape for Solar YouTube instance pools.
 */
public class InstancesUpdaterTest {

    @Test
    public void parseNotPipeJsonReadsThreeLists() throws Exception {
        String body = "{"
                + "\"invidious\":[\"http://a.example:3000\",\"http://b.example:3000\"],"
                + "\"piped\":[\"http://p.example,http://proxy.example\"],"
                + "\"ytapilegacy\":[\"http://yt.example:2823\"],"
                + "\"yt2009\":[\"http://ignored.example\"]"
                + "}";
        InstancesUpdater.ParsedInstances p = InstancesUpdater.parseNotPipeJson(body);
        if (p.invidious.size() != 2) {
            throw new AssertionError("expected 2 invidious, got " + p.invidious.size());
        }
        if (p.piped.size() != 1 || !p.piped.get(0).contains("proxy")) {
            throw new AssertionError("piped row missing: " + p.piped);
        }
        if (p.ytapi.size() != 1 || !p.ytapi.get(0).contains("2823")) {
            throw new AssertionError("ytapi missing: " + p.ytapi);
        }
    }

    @Test
    public void parseEmptyKeysFailOpen() throws Exception {
        String body = "{\"invidious\":[],\"piped\":[],\"ytapilegacy\":[]}";
        InstancesUpdater.ParsedInstances p = InstancesUpdater.parseNotPipeJson(body);
        if (!p.invidious.isEmpty() || !p.piped.isEmpty() || !p.ytapi.isEmpty()) {
            throw new AssertionError("expected empty lists");
        }
    }
}
