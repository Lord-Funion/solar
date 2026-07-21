package com.solar.launcher.stem;

import com.solar.launcher.PlayQueue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Stem/Mix share Music’s {@link PlayQueue} spine — clear+seed, live window, Next-up, hold-replace.
 * Layman: jam songs live in the same play queue as Now Playing; finished slots pull the next row.
 * Technical: Stem live=2 · Mix live=3; footer Add is adapter-only (not a queue index).
 * Was: jam tracks only in StemSession/MixSession slots. Reversal: ignore these helpers; keep slot-only lists.
 * 2026-07-21
 */
public final class StemMixQueuePolicy {
    /** StemFM-style live pair. 2026-07-21 */
    public static final int STEM_LIVE_WINDOW = 2;
    /**
     * Mix live decks (two discs). 2026-07-21 Stems/Mix sanity
     * Was: 3 (triple faders). Reversal: MIX_LIVE_WINDOW = 3.
     */
    public static final int MIX_LIVE_WINDOW = 2;

    private StemMixQueuePolicy() {}

    /**
     * Clear play queue and seed jam track files (session start).
     * Layman: wipe the old queue and drop in the songs you’re about to jam.
     * 2026-07-21
     */
    public static void clearAndSeed(PlayQueue queue, List<File> tracks) {
        if (queue == null) return;
        queue.clear();
        if (tracks == null) return;
        for (int i = 0; i < tracks.size(); i++) {
            File f = tracks.get(i);
            if (f != null && f.isFile()) {
                queue.append(PlayQueue.QueueItem.music(f));
            }
        }
        if (!queue.isEmpty()) queue.setIndex(0);
    }

    /**
     * Clear+seed from a sparse slot array (Mix assign / Stem pick).
     * Layman: only filled pads become queue rows, in pad order.
     * 2026-07-21
     */
    public static void clearAndSeedSlots(PlayQueue queue, File[] slots) {
        List<File> files = new ArrayList<File>();
        if (slots != null) {
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] != null && slots[i].isFile()) files.add(slots[i]);
            }
        }
        clearAndSeed(queue, files);
    }

    /** How many live slots this mode owns (2 Stem / 3 Mix). 2026-07-21 */
    public static int liveWindow(boolean mixMode) {
        return mixMode ? MIX_LIVE_WINDOW : STEM_LIVE_WINDOW;
    }

    /**
     * First queue index past the live window — the Next-up row (−1 if none).
     * Layman: song waiting in line after the ones currently on pads.
     * 2026-07-21
     */
    public static int nextUpIndex(PlayQueue queue, int liveWindow) {
        if (queue == null || liveWindow < 1) return -1;
        if (queue.size() <= liveWindow) return -1;
        return liveWindow;
    }

    /**
     * Display name for Next-up (empty when queue has no waiting track).
     * 2026-07-21
     */
    public static String nextUpLabel(PlayQueue queue, int liveWindow) {
        int i = nextUpIndex(queue, liveWindow);
        if (i < 0) return "";
        PlayQueue.QueueItem item = queue.items().get(i);
        return displayName(item);
    }

    /**
     * Pair/triple advance: queue index that should soft-replace a finished live slot.
     * Layman: when a pad’s song ends, pull the next waiting track into that pad.
     * Technical: nextUpIndex while size &gt; liveWindow; else −1 (pair-repeat or survivor).
     * 2026-07-21
     */
    public static int advanceSourceIndex(PlayQueue queue, int liveWindow) {
        return nextUpIndex(queue, liveWindow);
    }

    /**
     * Next-up advance preferring stem-ready overflow tracks (prep-aware).
     * Layman: skip songs still cooking when a ready one is waiting further down.
     * Technical: scan from nextUpIndex; first stemsReady[i]==true wins; else default next.
     * Was: always advanceSourceIndex (FIFO). Reversal: return advanceSourceIndex only.
     * @param stemsReady per-queue-index readiness (null → FIFO)
     * 2026-07-21
     */
    public static int advanceSourcePreferReady(PlayQueue queue, int liveWindow,
            boolean[] stemsReady) {
        int next = nextUpIndex(queue, liveWindow);
        if (next < 0) return -1;
        if (stemsReady == null || queue == null) return next;
        for (int i = next; i < queue.size(); i++) {
            if (i < stemsReady.length && stemsReady[i]) return i;
        }
        return next;
    }

    /**
     * True when prep-aware pick jumped past the plain Next-up head.
     * Layman: Up Next dial should flash red when readiness reordered the line.
     * 2026-07-21
     */
    public static boolean prepAwareReordered(int defaultNextIndex, int chosenIndex) {
        return defaultNextIndex >= 0 && chosenIndex >= 0 && chosenIndex != defaultNextIndex;
    }

    /**
     * Closed Stem pair (or single): finished song soft-restarts — no Next-up advance.
     * Layman: with only one or two songs queued, a finished track loops so unequal lengths keep mixing.
     * Technical: queueSize ≤ {@link #STEM_LIVE_WINDOW} → seek+fade on existing mixer, not softReplace.
     * Was: size≤2 fell through to survivor-only handoff (killed the short song). Reversal: always false.
     * 2026-07-21
     */
    public static boolean shouldPairRepeat(int queueSize) {
        return queueSize <= STEM_LIVE_WINDOW;
    }

    /**
     * Mix/Stem: restart ended live deck when queue has no overflow past the live window.
     * Layman: nothing waiting in line → that pad’s song starts over; partner keeps going.
     * 2026-07-21
     */
    public static boolean shouldLiveWindowRepeat(int queueSize, int liveWindow) {
        if (liveWindow < 1) return queueSize <= 0;
        return queueSize <= liveWindow;
    }

    /**
     * Song-end branch: soft-restart vs queue advance (Stem host + tests).
     * Layman: pick “loop this song” when the jam is a closed pair; else pull Next-up.
     * Technical: preferSoftRestart when shouldPairRepeat; else advance iff nextUpIndex ≥ 0.
     * 2026-07-21
     */
    public static boolean preferSoftRestartOverAdvance(int queueSize) {
        return shouldPairRepeat(queueSize);
    }

    /**
     * Soft-restart the finished live seat when the jam is a closed pair/single,
     * or when Next-up advance failed / nothing waiting.
     * Layman: that song fades back into itself; partner keeps playing.
     * Technical: shouldPairRepeat OR !advanceGotFile — never silence/hard-cut dead end.
     * Was: only pair-repeat; advance miss could mute. Reversal: return shouldPairRepeat only.
     * 2026-07-21
     */
    public static boolean softRestartFinishedSeat(int queueSize, boolean advanceReturnedFile) {
        if (preferSoftRestartOverAdvance(queueSize)) return true;
        return !advanceReturnedFile;
    }

    /**
     * Both pair seats use the same soft-restart rule (finished index does not matter).
     * Layman: song A or B ending both loop the same way in a 1–2 track jam.
     * 2026-07-21
     */
    public static boolean pairSoftRestartsEitherSeat(int queueSize, int finishedSongIndex) {
        if (finishedSongIndex < 0) return false;
        return preferSoftRestartOverAdvance(queueSize);
    }

    /**
     * After advancing slot {@code liveSlot} with queue item at {@code sourceIndex},
     * move that item into the live window and shift the finished track toward Next-up.
     * Layman: the new song takes the pad’s seat; the old one goes to the waiting line.
     * Technical: swap/move so indices [0..liveWindow) stay the live set; persist via caller.
     * 2026-07-21
     */
    public static boolean applyAdvanceOrder(PlayQueue queue, int liveSlot, int sourceIndex,
            int liveWindow) {
        if (queue == null) return false;
        if (liveSlot < 0 || liveSlot >= liveWindow) return false;
        if (sourceIndex < liveWindow || sourceIndex >= queue.size()) return false;
        // Swap finished live seat with Next-up (or deeper) source. 2026-07-21
        queue.swap(liveSlot, sourceIndex);
        return true;
    }

    /**
     * Hold-replace: move chosen queue track into a live slot; displaced track becomes Next-up head.
     * Layman: you pick another queued song for this pad; the old pad song waits next.
     * Technical: if pick already in live window, swap; else move pick → liveSlot then order Next-up.
     * Was: library browse reassign only. Reversal: drop; keep onRequestStemSongReassign browse.
     * 2026-07-21
     */
    public static boolean applyHoldReplaceOrder(PlayQueue queue, int liveSlot, int pickIndex,
            int liveWindow) {
        if (queue == null) return false;
        int size = queue.size();
        if (liveSlot < 0 || liveSlot >= liveWindow || liveSlot >= size) return false;
        if (pickIndex < 0 || pickIndex >= size) return false;
        if (pickIndex == liveSlot) return true;
        if (pickIndex < liveWindow) {
            // Swap two live pads. 2026-07-21
            queue.swap(liveSlot, pickIndex);
            return true;
        }
        // Move pick into live seat; former live item shifts toward Next-up. 2026-07-21
        queue.move(pickIndex, liveSlot);
        return true;
    }

    /**
     * Adapter row count including footer Add song (queueSize + 1, or 1 when empty).
     * Layman: last row is always “Add song”, not a track.
     * 2026-07-21
     */
    public static int adapterCountWithFooter(int queueSize) {
        if (queueSize < 0) queueSize = 0;
        return queueSize + 1;
    }

    /** True when adapter index is the Add song footer (not a PlayQueue index). 2026-07-21 */
    public static boolean isFooterIndex(int adapterIndex, int queueSize) {
        if (queueSize < 0) queueSize = 0;
        return adapterIndex == queueSize;
    }

    /**
     * Clamp focus to a real track index; never land on footer during moves.
     * Layman: while rearranging, highlight stays on songs only.
     * 2026-07-21
     */
    public static int clampTrackFocus(int focus, int queueSize) {
        if (queueSize <= 0) return 0;
        if (focus < 0) return 0;
        if (focus >= queueSize) return queueSize - 1;
        return focus;
    }

    /**
     * During ribbon move, footer must hide (non-selectable).
     * Layman: hide Add while you’re dragging tracks so you don’t pick it by mistake.
     * 2026-07-21
     */
    public static boolean footerVisible(boolean moveActive) {
        return !moveActive;
    }

    /** Human title for a queue row. 2026-07-21 */
    public static String displayName(PlayQueue.QueueItem item) {
        if (item == null) return "";
        if (item.kind == PlayQueue.ItemKind.MUSIC_FILE && item.file != null) {
            return StemControls.stripTrackDisplayName(item.file.getName());
        }
        String meta = item.streamMeta();
        return meta != null ? meta : "";
    }

    /**
     * Queue index of a music file (−1 if absent). Path match.
     * Layman: find whether this song is already waiting in the play queue.
     * 2026-07-21
     */
    public static int indexOfMusicFile(PlayQueue queue, File file) {
        if (queue == null || file == null) return -1;
        String path = file.getAbsolutePath();
        if (path == null) return -1;
        java.util.List<PlayQueue.QueueItem> items = queue.items();
        for (int i = 0; i < items.size(); i++) {
            PlayQueue.QueueItem it = items.get(i);
            if (it == null || it.file == null) continue;
            if (path.equals(it.file.getAbsolutePath())) return i;
        }
        return -1;
    }

    /**
     * Hold-replace pick: if file already queued, bring into liveSlot; else leave queue alone.
     * Layman: picking a queued song pulls it onto the pad seat without duplicating the row.
     * Technical: indexOf + {@link #applyHoldReplaceOrder}; returns pick index after move, or −1.
     * Was: always browse softReplace without queue reorder. Reversal: return −1 always.
     * 2026-07-21
     */
    public static int bringForwardIfQueued(PlayQueue queue, int liveSlot, File file,
            int liveWindow) {
        int pick = indexOfMusicFile(queue, file);
        if (pick < 0) return -1;
        if (!applyHoldReplaceOrder(queue, liveSlot, pick, liveWindow)) return -1;
        // After move/swap, liveSlot holds the pick. 2026-07-21
        return liveSlot;
    }

    /**
     * Soft-replace readiness: file present and (for Stem) stems ready flag from caller.
     * Layman: ready songs mix in now; others wait in line while they prep.
     * 2026-07-21
     */
    public static boolean canSoftReplaceNow(boolean fileReady, boolean stemsOrDeckReady) {
        return fileReady && stemsOrDeckReady;
    }

    /**
     * Keep Stem/Mix mixers attached while the library is open for queue Add or mid-jam replace.
     * Layman: picking another song must not kill the jam under the browser.
     * Was: any leave of STATE_STEM/MIX always detached hosts. Reversal: return false always.
     * 2026-07-21
     */
    public static boolean keepJamAliveUnderBrowse(boolean queueAppendBrowse,
            boolean stemSoftReassign, boolean mixDeckReassign) {
        return queueAppendBrowse || stemSoftReassign || mixDeckReassign;
    }

    /**
     * Queue Add pick must only append — never clear/seed/stop engines.
     * Layman: adding a waiting song leaves what’s playing alone.
     * Tech: callers use appendToMusicQueue; never clearAndSeed / stopCompeting.
     * 2026-07-21
     */
    public static boolean queueAppendMustNotInterruptPlayback() {
        return true;
    }

    /**
     * Stem queue-Add browse offers Has Stems; Mix / NP append does not.
     * Layman: only Stem cares about songs that already have pad files ready.
     * Was: Has Stems only in stemPickMode. Reversal: return false always.
     * 2026-07-21
     */
    public static boolean offerHasStemsInQueueAppend(boolean queueAppendBrowse,
            boolean returnToStem) {
        return queueAppendBrowse && returnToStem;
    }

    /**
     * Face / arch title with 1-based queue position: "(3) Song Name".
     * Layman: show where this track sits in the play queue on the remix face.
     * Was: bare display name. Reversal: return displayName only.
     * 2026-07-21
     */
    public static String titleWithQueuePosition(String displayName, int oneBasedPos) {
        String name = displayName != null ? displayName : "";
        if (oneBasedPos < 1) return name;
        if (name.length() == 0) return "(" + oneBasedPos + ")";
        return "(" + oneBasedPos + ") " + name;
    }

    /**
     * 1-based play-queue position for a file (−1 if not queued).
     * Layman: which number badge this song should wear on the Stem face.
     * 2026-07-21
     */
    public static int oneBasedQueuePosition(PlayQueue queue, File file) {
        int i = indexOfMusicFile(queue, file);
        return i < 0 ? -1 : i + 1;
    }

    /**
     * Session-start wait menu: offer choice when some queue songs already have stems
     * and the live pair still needs prep (would block).
     * Layman: if pads are ready on some tracks but not others, ask which to start with.
     * Was: always cook first two with no choice. Reversal: return false.
     * 2026-07-21
     */
    public static boolean offerStemWaitChoice(int queueStemmedCount, int liveNeedPrepCount) {
        return queueStemmedCount > 0 && liveNeedPrepCount > 0;
    }

    /**
     * “Songs with stems” / Has Stems catalog row — only when the library has any.
     * Layman: hide the browse row when there’s nothing stemmed on disk.
     * Was: always show. Reversal: return true always.
     * 2026-07-21
     */
    public static boolean offerWithStemsCatalogRow(boolean libraryHasStemmedTracks) {
        return libraryHasStemmedTracks;
    }

    /**
     * Build Stem start wait-menu labels: one per ready queue song + optional catalog row.
     * Layman: pick a ready track to mash now, or browse all stemmed songs.
     * Technical: stemmedNames in queue order; catalogLabel null/empty → omit last row.
     * 2026-07-21
     */
    public static String[] buildStemWaitMenuRows(List<String> stemmedDisplayNames,
            String withStemsCatalogLabel) {
        int n = stemmedDisplayNames != null ? stemmedDisplayNames.size() : 0;
        boolean cat = withStemsCatalogLabel != null && withStemsCatalogLabel.length() > 0;
        String[] out = new String[n + (cat ? 1 : 0)];
        for (int i = 0; i < n; i++) {
            String s = stemmedDisplayNames.get(i);
            out[i] = s != null ? s : "";
        }
        if (cat) out[n] = withStemsCatalogLabel;
        return out;
    }

    /**
     * First {@code liveWindow} files from an unlimited queue list (live mash pair).
     * Layman: only two songs play on pads; the rest wait in the numbered queue.
     * Was: truncate openStemPlayer at MAX_SONGS before seeding. Reversal: that trim.
     * 2026-07-21
     */
    public static List<File> liveWindowFiles(List<File> allQueued, int liveWindow) {
        List<File> out = new ArrayList<File>();
        if (allQueued == null || liveWindow < 1) return out;
        for (int i = 0; i < allQueued.size() && out.size() < liveWindow; i++) {
            File f = allQueued.get(i);
            if (f != null && f.isFile()) out.add(f);
        }
        return out;
    }

    /**
     * Hybrid start: begin mixers only after this many live tracks are prepared.
     * Layman: wait until both pad songs are ready, then keep cooking the rest quietly.
     * 2026-07-21
     */
    public static int hybridPrepGateCount(int liveTrackCount) {
        if (liveTrackCount < 1) return 0;
        if (liveTrackCount == 1) return 1;
        return STEM_LIVE_WINDOW;
    }

    /**
     * True when overflow queue tracks should keep preparing after live jam starts.
     * Layman: more songs in line → cook their stems in the background.
     * 2026-07-21
     */
    public static boolean shouldBackgroundPrepOverflow(int queueSize, int liveWindow) {
        return queueSize > liveWindow && liveWindow > 0;
    }

    /**
     * Queue OK during Stem/Mix must soft-replace the focused pad/deck — never NP takeover.
     * Layman: tapping a queued song mid-jam swaps that pad, not Now Playing.
     * Was: playUnifiedQueueItemAt always prepareMusicTrack. Reversal: return false.
     * 2026-07-21 Stems/Mix sanity
     */
    public static boolean queueOkOwnsJam(boolean stemOrMixSessionActive) {
        return stemOrMixSessionActive;
    }

    /**
     * Mid-Stem soft-replace / queue OK: refuse tracks without prepared pads.
     * Layman: only songs that already have stems can jump into a live jam.
     * Was: softReplaceSong cooked Lalal mid-jam. Reversal: return false always.
     * 2026-07-21 Stems/Mix sanity
     */
    public static boolean refuseUnstemmedMidStem(boolean stemsReadyOnDisk) {
        return !stemsReadyOnDisk;
    }

    /**
     * Mid-Stem queue Add footer must open prepared-only (Has Stems) browse.
     * Layman: adding mid-jam only offers songs that already have pads.
     * Was: full library root. Reversal: return false.
     * 2026-07-21 Stems/Mix sanity
     */
    public static boolean forcePreparedOnlyQueueAppend(boolean queueAppendBrowse,
            boolean returnToStem) {
        return queueAppendBrowse && returnToStem;
    }

    /**
     * Mix queue: Prev = deck 0, Next = deck 1 (1-based stamp later).
     * Layman: while the queue is open, Prev/Next pick which floating disc owns the song.
     * @return 0 for Prev, 1 for Next, −1 if neither
     * 2026-07-21 Stems/Mix sanity
     */
    public static int mixDeckIndexFromPrevNext(boolean isPrev, boolean isNext) {
        if (isPrev) return 0;
        if (isNext) return 1;
        return -1;
    }

    /**
     * Stamp queue pick onto a Mix deck seat and park it as Next-up head when past live window.
     * Layman: assign this queued song to disc 1 or 2 and pull it forward in line.
     * Technical: applyHoldReplaceOrder(liveSlot=deck, pickIndex); caller fades deck + refreshes titles.
     * Was: no queue Prev/Next deck stamp. Reversal: return false.
     * 2026-07-21 Stems/Mix sanity
     */
    public static boolean stampMixDeckAndBringForward(PlayQueue queue, int deckIndex,
            int pickIndex, int liveWindow) {
        if (queue == null) return false;
        if (deckIndex < 0 || deckIndex >= liveWindow) return false;
        return applyHoldReplaceOrder(queue, deckIndex, pickIndex, liveWindow);
    }

    /**
     * Append/replace mid-Stem: file must already be stem-ready.
     * Layman: don’t start a cloud split while pads are already rocking.
     * 2026-07-21 Stems/Mix sanity
     */
    public static boolean mayInsertMidStemJam(boolean stemsReady) {
        return stemsReady;
    }

    /**
     * Song-end: when Next-up advance fails, soft-restart the finished seat (never hard silence).
     * Layman: if the waiting song can’t load, that pad restarts its own track with a fade.
     * Was: survivor-only handoff (killed the short song). Reversal: return false.
     * 2026-07-21
     */
    public static boolean softRestartWhenAdvanceMisses(boolean advanceReturnedFile) {
        return !advanceReturnedFile;
    }

    /**
     * Overflow queue always has a Next-up seat to soft-replace into (swap loops the line).
     * Layman: with 3+ songs, ending a pad pulls the next waiting row — forever cycling.
     * Technical: size &gt; liveWindow → advanceSourceIndex ≥ liveWindow.
     * 2026-07-21
     */
    public static boolean queueAdvanceLoopsViaSwap(int queueSize, int liveWindow) {
        return queueSize > liveWindow && liveWindow > 0;
    }
}
