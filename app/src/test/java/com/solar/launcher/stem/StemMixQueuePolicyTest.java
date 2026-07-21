package com.solar.launcher.stem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.solar.launcher.PlayQueue;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Unified Stem/Mix queue spine — clear+seed, advance, hold-replace, footer index.
 * 2026-07-21
 */
public class StemMixQueuePolicyTest {

    private File dir;

    @Before
    public void setUp() throws Exception {
        dir = File.createTempFile("stemmixq", "");
        assertTrue(dir.delete());
        assertTrue(dir.mkdir());
    }

    private File track(String name) throws Exception {
        File f = new File(dir, name);
        FileOutputStream out = new FileOutputStream(f);
        out.write(1);
        out.close();
        return f;
    }

    @Test
    public void clearAndSeedWipesAndFills() throws Exception {
        PlayQueue q = new PlayQueue();
        q.append(PlayQueue.QueueItem.music(track("old.mp3")));
        List<File> jam = new ArrayList<File>();
        jam.add(track("a.mp3"));
        jam.add(track("b.mp3"));
        jam.add(track("c.mp3"));
        StemMixQueuePolicy.clearAndSeed(q, jam);
        assertEquals(3, q.size());
        assertEquals(0, q.index());
        assertEquals("a.mp3", q.items().get(0).file.getName());
    }

    @Test
    public void nextUpAndAdvanceSwap() throws Exception {
        PlayQueue q = new PlayQueue();
        List<File> jam = new ArrayList<File>();
        jam.add(track("live0.mp3"));
        jam.add(track("live1.mp3"));
        jam.add(track("next.mp3"));
        StemMixQueuePolicy.clearAndSeed(q, jam);
        assertEquals(2, StemMixQueuePolicy.nextUpIndex(q, StemMixQueuePolicy.STEM_LIVE_WINDOW));
        String label = StemMixQueuePolicy.nextUpLabel(q, 2);
        assertTrue(label.length() > 0);
        assertTrue(StemMixQueuePolicy.applyAdvanceOrder(q, 0, 2, 2));
        assertEquals("next.mp3", q.items().get(0).file.getName());
    }

    @Test
    public void holdReplaceMovesPickIntoLive() throws Exception {
        PlayQueue q = new PlayQueue();
        List<File> jam = new ArrayList<File>();
        jam.add(track("a.mp3"));
        jam.add(track("b.mp3"));
        jam.add(track("c.mp3"));
        StemMixQueuePolicy.clearAndSeed(q, jam);
        assertTrue(StemMixQueuePolicy.applyHoldReplaceOrder(q, 1, 2, 2));
        assertEquals("c.mp3", q.items().get(1).file.getName());
    }

    @Test
    public void footerIndexingContract() {
        assertEquals(4, StemMixQueuePolicy.adapterCountWithFooter(3));
        assertTrue(StemMixQueuePolicy.isFooterIndex(3, 3));
        assertFalse(StemMixQueuePolicy.isFooterIndex(2, 3));
        assertEquals(2, StemMixQueuePolicy.clampTrackFocus(99, 3));
        assertFalse(StemMixQueuePolicy.footerVisible(true));
        assertTrue(StemMixQueuePolicy.footerVisible(false));
    }

    @Test
    public void softReplaceReadiness() {
        assertTrue(StemMixQueuePolicy.canSoftReplaceNow(true, true));
        assertFalse(StemMixQueuePolicy.canSoftReplaceNow(true, false));
    }

    @Test
    public void keepJamAliveUnderBrowseGates() {
        // Queue Add or mid-jam replace keep mixers; idle browse does not. 2026-07-21
        assertTrue(StemMixQueuePolicy.keepJamAliveUnderBrowse(true, false, false));
        assertTrue(StemMixQueuePolicy.keepJamAliveUnderBrowse(false, true, false));
        assertTrue(StemMixQueuePolicy.keepJamAliveUnderBrowse(false, false, true));
        assertFalse(StemMixQueuePolicy.keepJamAliveUnderBrowse(false, false, false));
        assertTrue(StemMixQueuePolicy.queueAppendMustNotInterruptPlayback());
    }

    /** Has Stems only on Stem queue-Add — Mix / idle append skip. 2026-07-21 */
    @Test
    public void offerHasStemsOnlyForStemQueueAppend() {
        assertTrue(StemMixQueuePolicy.offerHasStemsInQueueAppend(true, true));
        assertFalse(StemMixQueuePolicy.offerHasStemsInQueueAppend(true, false));
        assertFalse(StemMixQueuePolicy.offerHasStemsInQueueAppend(false, true));
        assertFalse(StemMixQueuePolicy.offerHasStemsInQueueAppend(false, false));
    }

    @Test
    public void shouldPairRepeatClosedJam() {
        // 0–2 → soft-restart; 3+ → Next-up advance path. 2026-07-21
        assertTrue(StemMixQueuePolicy.shouldPairRepeat(0));
        assertTrue(StemMixQueuePolicy.shouldPairRepeat(1));
        assertTrue(StemMixQueuePolicy.shouldPairRepeat(2));
        assertFalse(StemMixQueuePolicy.shouldPairRepeat(3));
        assertFalse(StemMixQueuePolicy.shouldPairRepeat(4));
        assertTrue(StemMixQueuePolicy.preferSoftRestartOverAdvance(2));
        assertFalse(StemMixQueuePolicy.preferSoftRestartOverAdvance(3));
        // Either seat soft-restarts in a pair; self-loop when advance misses. 2026-07-21
        assertTrue(StemMixQueuePolicy.pairSoftRestartsEitherSeat(2, 0));
        assertTrue(StemMixQueuePolicy.pairSoftRestartsEitherSeat(2, 1));
        assertFalse(StemMixQueuePolicy.pairSoftRestartsEitherSeat(3, 0));
        assertTrue(StemMixQueuePolicy.softRestartFinishedSeat(2, true));
        assertTrue(StemMixQueuePolicy.softRestartFinishedSeat(5, false));
        assertFalse(StemMixQueuePolicy.softRestartFinishedSeat(5, true));
        // MIX_LIVE_WINDOW=2 (Stems/Mix sanity). Was: window 3 → repeat at size≤3. 2026-07-21
        assertTrue(StemMixQueuePolicy.shouldLiveWindowRepeat(2, StemMixQueuePolicy.MIX_LIVE_WINDOW));
        assertFalse(StemMixQueuePolicy.shouldLiveWindowRepeat(3, StemMixQueuePolicy.MIX_LIVE_WINDOW));
        assertEquals(2, StemMixQueuePolicy.MIX_LIVE_WINDOW);
        assertEquals(2, StemMixQueuePolicy.liveWindow(true));
    }

    /** Queue OK owns jam; refuse unstemmed; prepared-only append; Mix Prev/Next stamp. 2026-07-21 */
    @Test
    public void queueOkAndPreparedGates() throws Exception {
        assertTrue(StemMixQueuePolicy.queueOkOwnsJam(true));
        assertFalse(StemMixQueuePolicy.queueOkOwnsJam(false));
        assertTrue(StemMixQueuePolicy.refuseUnstemmedMidStem(false));
        assertFalse(StemMixQueuePolicy.refuseUnstemmedMidStem(true));
        assertTrue(StemMixQueuePolicy.forcePreparedOnlyQueueAppend(true, true));
        assertFalse(StemMixQueuePolicy.forcePreparedOnlyQueueAppend(true, false));
        assertFalse(StemMixQueuePolicy.mayInsertMidStemJam(false));
        assertTrue(StemMixQueuePolicy.mayInsertMidStemJam(true));
        assertEquals(0, StemMixQueuePolicy.mixDeckIndexFromPrevNext(true, false));
        assertEquals(1, StemMixQueuePolicy.mixDeckIndexFromPrevNext(false, true));
        assertEquals(-1, StemMixQueuePolicy.mixDeckIndexFromPrevNext(false, false));

        PlayQueue q = new PlayQueue();
        File a = track("a.mp3");
        File b = track("b.mp3");
        File c = track("c.mp3");
        List<File> jam = new ArrayList<File>();
        jam.add(a);
        jam.add(b);
        jam.add(c);
        StemMixQueuePolicy.clearAndSeed(q, jam);
        assertTrue(StemMixQueuePolicy.stampMixDeckAndBringForward(
                q, 0, 2, StemMixQueuePolicy.MIX_LIVE_WINDOW));
        assertEquals("c.mp3", q.items().get(0).file.getName());
    }

    @Test
    public void handoffBranchPairVsAdvance() throws Exception {
        PlayQueue pair = new PlayQueue();
        List<File> two = new ArrayList<File>();
        two.add(track("a.mp3"));
        two.add(track("b.mp3"));
        StemMixQueuePolicy.clearAndSeed(pair, two);
        assertTrue(StemMixQueuePolicy.preferSoftRestartOverAdvance(pair.size()));
        assertEquals(-1, StemMixQueuePolicy.advanceSourceIndex(
                pair, StemMixQueuePolicy.STEM_LIVE_WINDOW));

        PlayQueue overflow = new PlayQueue();
        List<File> three = new ArrayList<File>();
        three.add(track("a.mp3"));
        three.add(track("b.mp3"));
        three.add(track("c.mp3"));
        StemMixQueuePolicy.clearAndSeed(overflow, three);
        assertFalse(StemMixQueuePolicy.preferSoftRestartOverAdvance(overflow.size()));
        assertEquals(2, StemMixQueuePolicy.advanceSourceIndex(
                overflow, StemMixQueuePolicy.STEM_LIVE_WINDOW));
    }

    /** Bring-forward when pick already queued; miss stays −1. 2026-07-21 */
    @Test
    public void bringForwardIfAlreadyQueued() throws Exception {
        PlayQueue q = new PlayQueue();
        File a = track("a.mp3");
        File b = track("b.mp3");
        File c = track("c.mp3");
        List<File> jam = new ArrayList<File>();
        jam.add(a);
        jam.add(b);
        jam.add(c);
        StemMixQueuePolicy.clearAndSeed(q, jam);
        assertEquals(2, StemMixQueuePolicy.indexOfMusicFile(q, c));
        assertEquals(1, StemMixQueuePolicy.bringForwardIfQueued(
                q, 1, c, StemMixQueuePolicy.STEM_LIVE_WINDOW));
        assertEquals("c.mp3", q.items().get(1).file.getName());
        assertEquals(-1, StemMixQueuePolicy.bringForwardIfQueued(
                q, 0, track("missing.mp3"), StemMixQueuePolicy.STEM_LIVE_WINDOW));
    }

    /** Prep-aware Next-up prefers ready overflow; alert when reorder fires. 2026-07-21 */
    @Test
    public void advancePreferReadyAndAlert() throws Exception {
        PlayQueue q = new PlayQueue();
        List<File> jam = new ArrayList<File>();
        jam.add(track("live0.mp3"));
        jam.add(track("live1.mp3"));
        jam.add(track("prep.mp3"));
        jam.add(track("ready.mp3"));
        StemMixQueuePolicy.clearAndSeed(q, jam);
        boolean[] ready = new boolean[] { true, true, false, true };
        int def = StemMixQueuePolicy.advanceSourceIndex(q, StemMixQueuePolicy.STEM_LIVE_WINDOW);
        assertEquals(2, def);
        int pref = StemMixQueuePolicy.advanceSourcePreferReady(
                q, StemMixQueuePolicy.STEM_LIVE_WINDOW, ready);
        assertEquals(3, pref);
        assertTrue(StemMixQueuePolicy.prepAwareReordered(def, pref));
        assertFalse(StemMixQueuePolicy.prepAwareReordered(2, 2));
        // All unready → fall open to FIFO next. 2026-07-21
        boolean[] none = new boolean[] { true, true, false, false };
        assertEquals(2, StemMixQueuePolicy.advanceSourcePreferReady(
                q, StemMixQueuePolicy.STEM_LIVE_WINDOW, none));
    }

    /** Queue position titles + wait-menu / hybrid prep helpers. 2026-07-21 */
    @Test
    public void queuePositionTitlesAndWaitMenu() throws Exception {
        assertEquals("(3) Hello", StemMixQueuePolicy.titleWithQueuePosition("Hello", 3));
        assertEquals("Hello", StemMixQueuePolicy.titleWithQueuePosition("Hello", 0));
        assertEquals("(2)", StemMixQueuePolicy.titleWithQueuePosition("", 2));

        PlayQueue q = new PlayQueue();
        File a = track("a.mp3");
        File b = track("b.mp3");
        File c = track("c.mp3");
        List<File> jam = new ArrayList<File>();
        jam.add(a);
        jam.add(b);
        jam.add(c);
        StemMixQueuePolicy.clearAndSeed(q, jam);
        assertEquals(1, StemMixQueuePolicy.oneBasedQueuePosition(q, a));
        assertEquals(3, StemMixQueuePolicy.oneBasedQueuePosition(q, c));

        assertTrue(StemMixQueuePolicy.offerStemWaitChoice(2, 1));
        assertFalse(StemMixQueuePolicy.offerStemWaitChoice(0, 2));
        assertFalse(StemMixQueuePolicy.offerStemWaitChoice(3, 0));
        assertTrue(StemMixQueuePolicy.offerWithStemsCatalogRow(true));
        assertFalse(StemMixQueuePolicy.offerWithStemsCatalogRow(false));

        List<String> names = new ArrayList<String>();
        names.add("(1) Ready A");
        names.add("(4) Ready B");
        String[] withCat = StemMixQueuePolicy.buildStemWaitMenuRows(names, "Songs with stems");
        assertEquals(3, withCat.length);
        assertEquals("Songs with stems", withCat[2]);
        String[] noCat = StemMixQueuePolicy.buildStemWaitMenuRows(names, null);
        assertEquals(2, noCat.length);

        List<File> live = StemMixQueuePolicy.liveWindowFiles(jam, 2);
        assertEquals(2, live.size());
        assertEquals(2, StemMixQueuePolicy.hybridPrepGateCount(5));
        assertEquals(1, StemMixQueuePolicy.hybridPrepGateCount(1));
        assertTrue(StemMixQueuePolicy.shouldBackgroundPrepOverflow(5, 2));
        assertFalse(StemMixQueuePolicy.shouldBackgroundPrepOverflow(2, 2));
    }

    /** Advance miss soft-restarts; overflow queue loops via swap. 2026-07-21 */
    @Test
    public void advanceMissAndQueueLoopPolicy() throws Exception {
        assertTrue(StemMixQueuePolicy.softRestartWhenAdvanceMisses(false));
        assertFalse(StemMixQueuePolicy.softRestartWhenAdvanceMisses(true));
        assertTrue(StemMixQueuePolicy.queueAdvanceLoopsViaSwap(3, 2));
        assertFalse(StemMixQueuePolicy.queueAdvanceLoopsViaSwap(2, 2));
        PlayQueue q = new PlayQueue();
        List<File> jam = new ArrayList<File>();
        jam.add(track("a.mp3"));
        jam.add(track("b.mp3"));
        jam.add(track("c.mp3"));
        StemMixQueuePolicy.clearAndSeed(q, jam);
        // Cycle through overflow twice — swap keeps Next-up forever. 2026-07-21
        assertTrue(StemMixQueuePolicy.applyAdvanceOrder(q, 0, 2, 2));
        assertEquals("c.mp3", q.items().get(0).file.getName());
        assertTrue(StemMixQueuePolicy.applyAdvanceOrder(q, 0, 2, 2));
        assertEquals("a.mp3", q.items().get(0).file.getName());
    }
}
