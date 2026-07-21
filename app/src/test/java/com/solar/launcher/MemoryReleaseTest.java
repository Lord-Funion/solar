package com.solar.launcher;

import android.content.ComponentCallbacks2;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 2026-07-20 — MemoryRelease ladder order + severe classification. */
public class MemoryReleaseTest {

    @After
    public void tearDown() {
        MemoryRelease.resetForTest();
    }

    @Test
    public void selfCheckOrder() {
        MemoryRelease.selfCheck();
    }

    @Test
    public void severeLevels() {
        assertTrue(MemoryRelease.isSevere(ComponentCallbacks2.TRIM_MEMORY_COMPLETE));
        assertTrue(MemoryRelease.isSevere(ComponentCallbacks2.TRIM_MEMORY_MODERATE));
        assertTrue(MemoryRelease.isSevere(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL));
        assertFalse(MemoryRelease.isSevere(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW));
        assertFalse(MemoryRelease.isSevere(-1));
    }

    @Test
    public void softTrimOrderSkipsDuplicates() {
        final AtomicInteger dups = new AtomicInteger();
        MemoryRelease.setHost(new MemoryRelease.Host() {
            @Override
            public void releaseFlowBake() {}

            @Override
            public void releaseFlowCoverKeepNp() {}

            @Override
            public void shrinkLibrarySegments(boolean severe) {
                assertFalse(severe);
            }

            @Override
            public void dropFlowDuplicates() {
                dups.incrementAndGet();
            }
        });
        List<String> soft = MemoryRelease.release(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
        assertEquals(MemoryRelease.Step.FLOW_BAKE.name().toLowerCase(), soft.get(0));
        assertEquals(MemoryRelease.Step.FLOW_COVER.name().toLowerCase(), soft.get(1));
        assertTrue(soft.contains("theme"));
        assertTrue(soft.contains("library_segments"));
        assertFalse(soft.contains("flow_duplicates"));
        assertEquals(0, dups.get());
        assertEquals(MemoryRelease.Step.YT_PAUSE,
                MemoryRelease.lastOrderSnapshot().get(
                        MemoryRelease.lastOrderSnapshot().size() - 1));
    }

    @Test
    public void severeTrimDropsDuplicatesBeforeYtPause() {
        final AtomicInteger dups = new AtomicInteger();
        MemoryRelease.setHost(new MemoryRelease.Host() {
            @Override
            public void releaseFlowBake() {}

            @Override
            public void releaseFlowCoverKeepNp() {}

            @Override
            public void shrinkLibrarySegments(boolean severe) {
                assertTrue(severe);
            }

            @Override
            public void dropFlowDuplicates() {
                dups.incrementAndGet();
            }
        });
        List<String> hard = MemoryRelease.release(ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
        assertEquals(1, dups.get());
        assertTrue(hard.contains("flow_duplicates"));
        assertEquals("yt_pause", hard.get(hard.size() - 1));
    }

    @Test
    public void hooksStillFire() {
        final AtomicInteger hookCount = new AtomicInteger();
        MemoryRelease.register(new MemoryRelease.Hook() {
            @Override
            public void onMemoryRelease(int trimLevel, boolean severe) {
                if (severe) hookCount.incrementAndGet();
            }
        });
        MemoryRelease.release(ComponentCallbacks2.TRIM_MEMORY_COMPLETE);
        assertEquals(1, hookCount.get());
    }
}
