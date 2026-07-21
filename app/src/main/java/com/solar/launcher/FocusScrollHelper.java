package com.solar.launcher;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ListView;
import android.widget.ScrollView;

/**
 * 2026-07-11 — Edge-only scroll-to-focus for single-axis hardware lists.
 * PR #23: reuse one Handler + WeakHashMap of list focus requests (no alloc per tick).
 * 2026-07-11 — Edge-only ensure-visible (iPod/Android): focus walks the screen; list moves
 * only when the row would clip past the top or bottom of the viewport.
 * 2026-07-11 — ListView edge sticky-slot: highlight stays fixed while content ticks
 * (train-timetable / queue-ribbon feel). Was: sticky-Y every mid-list tick.
 * 2026-07-11 — Sticky Y = natural last/first fully-visible row top (GIF), not re-pin to
 * viewport-rowH-pad (that jumped the bar one slot then bounced). Pad only after a real clip.
 * 2026-07-11 — Ticker ticks use instant setSelectionFromTop / scrollTo only — no
 * smoothScrollBy / PositionScroller (those shunted the highlight during animation).
 * Reversal: restore FOCUS_SCROLL_MS glide path if a future build wants easing again.
 */
public final class FocusScrollHelper {
    /** ~2dp pad so focused row is not flush against the clip edge after a real scroll. */
    private static final float ENSURE_VISIBLE_PAD_DP = 2f;
    // 2026-07-11 — Lazy MAIN so pure computeEnsureVisibleScrollY loads in JVM unit tests.
    private static Handler mainHandler;
    private static final java.util.WeakHashMap<ListView, ListFocusRequest> REQUESTS =
            new java.util.WeakHashMap<ListView, ListFocusRequest>();
    private static final Rect TMP_RECT = new Rect();

    private FocusScrollHelper() {}

    /** 2026-07-11 — Main looper Handler; created on first ListView focus path. */
    private static Handler main() {
        if (mainHandler == null) {
            mainHandler = new Handler(Looper.getMainLooper());
        }
        return mainHandler;
    }

    /**
     * 2026-07-11 — Kept for tests / Chip mirrors; ticker path is always instant.
     * Layman: spinning the wheel never stacks a glide (there is no glide).
     * Technical: always true — preferInstantScroll documents the snappy-tick contract.
     */
    public static boolean preferInstantScroll(long nowMs, long lastMs, int rapidWindowMs) {
        return true;
    }

    /** 2026-07-11 — Was glide ms; ticker is 0 (instant). Tests assert non-positive. */
    public static int focusScrollDurationMs() {
        return 0;
    }

    /** 2026-07-11 — Legacy rapid window; unused by ticker path (always instant). */
    public static int rapidWheelScrollMs() {
        return 0;
    }

    public static void smoothScrollListToPosition(final ListView list, final int position) {
        if (list == null || position < 0) return;
        // 2026-07-11 — Name kept; body is ticker sticky (not PositionScroller).
        ensureListPositionVisible(list, position);
    }

    public static void focusListPosition(ListView list, int position) {
        if (list == null || position < 0) return;
        // 2026-07-21 — Seed/focus paths still requestFocus (idle chrome).
        ensureListPositionVisible(list, position, true);
    }

    /**
     * 2026-07-21 — Select + pin with optional requestFocus (list wheel mid-spin).
     * Layman: move the highlight on screen; real Android focus can wait until you pause.
     * Technical: {@code requestFocus=false} skips mid-list requestFocus and edge clear/abort/post.
     * Reversal: always pass true (pre–ListWheelChromePolicy behaviour).
     */
    public static void focusListPosition(ListView list, int position, boolean requestFocus) {
        if (list == null || position < 0) return;
        ensureListPositionVisible(list, position, requestFocus);
    }

    /**
     * 2026-07-11 — Pure edge-only scroll math: return scrollY that keeps child in viewport.
     * Unchanged when already fully visible; clamps to content bounds.
     * Pad used only when aligning after a real clip — never to trigger pre-scroll.
     * Layman: only nudge the page when the highlight would go off-screen.
     * Technical: ensure-visible clamp; no center-lock / bottom-align / pad-gated scroll.
     */
    public static int computeEnsureVisibleScrollY(int scrollY, int viewportH, int contentH,
            int childTop, int childBottom, int padPx) {
        if (viewportH <= 0) return scrollY;
        int pad = Math.max(0, padPx);
        int maxScroll = Math.max(0, contentH - viewportH);
        int next = scrollY;
        // 2026-07-11 — Trigger on hard clip only (GIF: no pre-scroll). Was: pad inset trigger.
        if (childTop < scrollY) {
            next = Math.max(0, childTop - pad);
        } else if (childBottom > scrollY + viewportH) {
            next = childBottom - viewportH + pad;
        }
        if (next < 0) next = 0;
        if (next > maxScroll) next = maxScroll;
        return next;
    }

    /**
     * 2026-07-20 — True when the row already sits fully inside the ScrollView window.
     * Layman: highlight can hop here without moving the list.
     * Technical: content-space tops vs scrollY..scrollY+viewport. Reversal: always false.
     */
    public static boolean isFullyVisibleInScroll(int scrollY, int viewportH,
            int childTop, int childBottom) {
        if (viewportH <= 0) return true;
        return childTop >= scrollY && childBottom <= scrollY + viewportH;
    }

    /**
     * 2026-07-20 — Child top/bottom in the scrolled content’s coordinate space.
     * Layman: where the row sits on the long paper inside the window.
     * Technical: sum getTop() up to ScrollView’s direct child (not viewport-relative).
     * Was: offsetDescendantRectToMyCoords alone could disagree with scrollY on API 17.
     */
    public static void contentTopBottom(ScrollView scroll, View child, int[] outTopBottom) {
        if (outTopBottom == null || outTopBottom.length < 2) return;
        outTopBottom[0] = 0;
        outTopBottom[1] = 0;
        if (scroll == null || child == null) return;
        View content = scroll.getChildCount() > 0 ? scroll.getChildAt(0) : null;
        if (content == null) {
            outTopBottom[0] = child.getTop();
            outTopBottom[1] = child.getBottom();
            return;
        }
        int top = 0;
        View v = child;
        while (v != null && v != content && v != scroll) {
            top += v.getTop();
            Object p = v.getParent();
            if (!(p instanceof View)) break;
            v = (View) p;
        }
        outTopBottom[0] = top;
        outTopBottom[1] = top + Math.max(0, child.getHeight());
    }

    /**
     * 2026-07-11 — Fallback sticky Y when no fully-visible row exists yet.
     * Top edge → pad; bottom edge → viewport - rowH - pad.
     * Layman: park the bar in a default hole if we cannot read the live slot.
     * Technical: setSelectionFromTop Y fallback; prefer computeStickySlotTop.
     */
    public static int computeStickyEdgeTop(int viewportH, int rowHeightHint, int padPx,
            boolean pinBottom) {
        int pad = Math.max(0, padPx);
        if (viewportH <= 0) return pad;
        int rowH = rowHeightHint > 0 ? rowHeightHint : Math.max(1, viewportH / 6);
        if (pinBottom) return Math.max(pad, viewportH - rowH - pad);
        return pad;
    }

    /**
     * 2026-07-11 — True iPod sticky slot: keep the natural last/first fully-visible top.
     * Continual edge ticks reuse that Y so the highlight does not jump then bounce.
     * Falls back to computeStickyEdgeTop when no full row is on screen yet.
     * Layman: leave the blue bar where it already sits; slide titles through it.
     * Technical: preserve lastFullyVisibleTop / firstFullyVisibleTop across setSelectionFromTop.
     */
    public static int computeStickySlotTop(int viewportH, int naturalSlotTop,
            boolean hasNaturalSlot, int rowHeightHint, int padPx, boolean pinBottom) {
        int rowH = rowHeightHint > 0 ? rowHeightHint : Math.max(1, viewportH > 0 ? viewportH / 6 : 1);
        int slot;
        if (hasNaturalSlot) {
            slot = naturalSlotTop;
        } else {
            slot = computeStickyEdgeTop(viewportH, rowH, padPx, pinBottom);
        }
        if (viewportH <= 0) return Math.max(0, slot);
        // 2026-07-11 — Tall next row must still fit in the viewport at this Y.
        if (pinBottom && slot + rowH > viewportH) {
            slot = Math.max(0, viewportH - rowH);
        }
        if (!pinBottom && slot < 0) slot = 0;
        return slot;
    }

    /**
     * 2026-07-11 — Pure ListView setSelectionFromTop Y for edge-only focus.
     * Fully on-screen (flush to edges OK) → keep {@code childTopInViewport}.
     * Clips top → sticky top slot; clips bottom / below → sticky bottom slot.
     * {@code naturalSlotTop}/{@code hasNaturalSlot} = live first/last fully-visible top.
     * Layman: highlight walks every row on the page; list only shifts at the edges.
     * Technical: iPod classic selection; pad only in fallback sticky math.
     */
    public static int computeListSelectionTop(int viewportH, int childTopInViewport,
            int childBottomInViewport, boolean childLaidOut, boolean positionAboveWindow,
            int rowHeightHint, int padPx, int naturalSlotTop, boolean hasNaturalSlot) {
        int pad = Math.max(0, padPx);
        if (viewportH <= 0) return pad;
        int rowH = rowHeightHint > 0 ? rowHeightHint : Math.max(1, viewportH / 6);
        if (childLaidOut) {
            int h = Math.max(1, childBottomInViewport - childTopInViewport);
            // Flush to 0 / viewport counts as visible — do not re-pin (avoids lock-to-slot).
            if (childTopInViewport >= 0 && childBottomInViewport <= viewportH) {
                return childTopInViewport;
            }
            if (childTopInViewport < 0) {
                return computeStickySlotTop(viewportH, naturalSlotTop, hasNaturalSlot, h, pad, false);
            }
            return computeStickySlotTop(viewportH, naturalSlotTop, hasNaturalSlot, h, pad, true);
        }
        if (positionAboveWindow) {
            return computeStickySlotTop(
                    viewportH, naturalSlotTop, hasNaturalSlot, rowH, pad, false);
        }
        return computeStickySlotTop(viewportH, naturalSlotTop, hasNaturalSlot, rowH, pad, true);
    }

    /**
     * 2026-07-11 — Compat overload without natural slot (tests / callers); uses fallback pin.
     * Reversal: pass natural last/first fully-visible tops for GIF-stable sticky.
     */
    public static int computeListSelectionTop(int viewportH, int childTopInViewport,
            int childBottomInViewport, boolean childLaidOut, boolean positionAboveWindow,
            int rowHeightHint, int padPx) {
        return computeListSelectionTop(viewportH, childTopInViewport, childBottomInViewport,
                childLaidOut, positionAboveWindow, rowHeightHint, padPx, 0, false);
    }

    /**
     * 2026-07-11/20 — Scroll only if focused child clips past top/bottom of ScrollView.
     * Instant scrollTo (ticker): content jumps so the focused row parks at the edge —
     * no smoothScrollTo highlight shunt. Was: isolated ticks used smoothScrollTo.
     * 2026-07-20 — Content-space tops (not viewport-relative) so mid-list ticks never scroll.
     * Layman: wheel moves the blue bar; the list only shifts at the top/bottom edge.
     */
    public static void ensureChildVisible(ScrollView scroll, View child) {
        if (scroll == null || child == null) return;
        int viewport = scroll.getHeight();
        if (viewport <= 0) return;
        View content = scroll.getChildCount() > 0 ? scroll.getChildAt(0) : null;
        int contentH = content != null ? content.getHeight() : 0;
        float density = scroll.getResources().getDisplayMetrics().density;
        int pad = Math.max(1, (int) (ENSURE_VISIBLE_PAD_DP * density));
        int scrollY = scroll.getScrollY();
        // 2026-07-20 — Prefer content-space walk; offsetDescendantRect kept as fallback.
        int[] tb = new int[2];
        contentTopBottom(scroll, child, tb);
        int childTop = tb[0];
        int childBottom = tb[1];
        if (childBottom <= childTop) {
            synchronized (TMP_RECT) {
                child.getDrawingRect(TMP_RECT);
                try {
                    scroll.offsetDescendantRectToMyCoords(child, TMP_RECT);
                } catch (Exception e) {
                    TMP_RECT.set(0, child.getTop(), child.getWidth(), child.getBottom());
                }
                childTop = TMP_RECT.top;
                childBottom = TMP_RECT.bottom;
            }
        }
        if (isFullyVisibleInScroll(scrollY, viewport, childTop, childBottom)) {
            return; // mid-list: focus only — do not move the viewport
        }
        if (contentH <= 0) contentH = Math.max(childBottom, scrollY + viewport);
        int targetY = computeEnsureVisibleScrollY(
                scrollY, viewport, contentH, childTop, childBottom, pad);
        if (targetY == scrollY) return;
        scroll.scrollTo(0, targetY);
    }

    /**
     * 2026-07-20 — requestFocus without mid-list ScrollView jump.
     * Layman: move the blue bar; don’t slide the whole menu unless the row is off-screen.
     * Technical: if already fully visible, undo platform bring-into-view after focus.
     * Reversal: row.requestFocus() + ensureChildVisible always.
     * @return true if the row took focus
     */
    public static boolean requestFocusEdgeOnly(ScrollView scroll, View row) {
        if (row == null) return false;
        if (scroll == null) {
            return row.requestFocus();
        }
        int sy = scroll.getScrollY();
        int vh = scroll.getHeight();
        int[] tb = new int[2];
        contentTopBottom(scroll, row, tb);
        boolean fully = isFullyVisibleInScroll(sy, vh, tb[0], tb[1]);
        if (!row.requestFocus()) return false;
        if (fully) {
            if (scroll.getScrollY() != sy) {
                scroll.scrollTo(0, sy);
            }
            return true;
        }
        ensureChildVisible(scroll, row);
        return true;
    }

    /**
     * 2026-07-11 — Select ListView row with edge-only ticker scroll (iPod / timetable).
     * Fully on-screen → preserve first-visible window, then select (no Y jump).
     * Off top/bottom → pin sticky slot so highlight stays put while content ticks.
     * Always instant setSelectionFromTop — never PositionScroller / smoothScrollBy.
     * Was: ~180ms glide that shunted focus chrome. Reversal: restore glide branch.
     * Clears child focus first + suppresses rect-scroll so chrome does not dual-flash.
     */
    public static void ensureListPositionVisible(ListView list, int position) {
        ensureListPositionVisible(list, position, true);
    }

    /**
     * 2026-07-11/21 — Select + pin; optional skip of requestFocus / edge focus storm.
     * Layman: keep the chosen row on screen; while spinning, do not re-light focus every notch.
     * Technical: mid-spin ({@code requestFocus=false}) still setSelectionFromTop; omits
     * mid-list requestFocus and edge clearFocus/abortListMotion/focusAfterSelect post.
     * Reversal: always call two-arg overload (requestFocus true).
     */
    public static void ensureListPositionVisible(ListView list, int position,
            boolean requestFocus) {
        if (list == null || position < 0) return;
        int count = list.getCount();
        if (count <= 0 || position >= count) return;
        // 2026-07-20 — Agent metering stripped (was timing + NDJSON on every wheel select).
        // 2026-07-17 — Before first layout (childCount==0) still arm selection so wheel
        // progress is not stuck at 0 until a later frame (real KEY path + inject harness).
        if (list.getChildCount() <= 0) {
            list.setSelection(position);
            if (requestFocus) {
                requestFor(list).focusAfterSelect(position, false);
            }
            return;
        }
        int viewport = list.getHeight();
        float density = list.getResources().getDisplayMetrics().density;
        int pad = Math.max(1, (int) (ENSURE_VISIBLE_PAD_DP * density));
        int first = list.getFirstVisiblePosition();
        int childIdx = position - first;
        View child = (childIdx >= 0 && childIdx < list.getChildCount())
                ? list.getChildAt(childIdx) : null;
        // 2026-07-11 — Bottom pin uses last visible row H; top pin uses first (variable rows).
        boolean above = position < first;
        int rowHint = 0;
        if (list.getChildCount() > 0) {
            View sample = above
                    ? list.getChildAt(0)
                    : list.getChildAt(list.getChildCount() - 1);
            if (sample != null) rowHint = sample.getHeight();
        }
        boolean laidOut = child != null;
        int top = laidOut ? child.getTop() : 0;
        int bottom = laidOut ? child.getBottom() : 0;
        boolean fullyVisible = laidOut && top >= 0 && bottom <= viewport;
        if (fullyVisible) {
            // 2026-07-16 — Mid-list walk: no clearFocus/abort/post (was ~1 full frame per wheel notch).
            // Layman: highlight just hops to the next already-on-screen row.
            // Technical: setSelectionFromTop + optional requestFocus; edge path still sticky.
            int sel = list.getSelectedItemPosition();
            if (sel != position) {
                list.setSelectionFromTop(position, top);
            }
            // 2026-07-21 — Mid-spin skips requestFocus (dual-line bind uses selection chrome).
            if (requestFocus && child != null && !child.isFocused() && child.isFocusable()) {
                child.requestFocus();
            }
            return;
        }
        // Walk the page: only pin to the absolute edge if clipping/off-screen.
        boolean pinBottom;
        if (laidOut) {
            pinBottom = top >= 0; // clips bottom (or below) vs clips top
        } else if (above) {
            pinBottom = false;
        } else {
            pinBottom = true;
        }
        int rowH = laidOut ? Math.max(1, bottom - top) : Math.max(1, rowHint);
        int selTop = pinBottom ? Math.max(pad, viewport - rowH - pad) : pad;
        if (!requestFocus) {
            // 2026-07-21 — Spinning edge: pin viewport only (no clearFocus/abort/focus post).
            // Layman: slide titles through the bar without a one-frame focus flash.
            // Was: clearListChildFocus + abortListMotion + focusAfterSelect every edge tick.
            armRectSuppress(list);
            list.setSelectionFromTop(position, selTop);
            if (list instanceof Y1SafeListView) {
                ((Y1SafeListView) list).setSuppressChildRectScroll(false);
            }
            return;
        }
        // 2026-07-11 — Drop old focus chrome first so the bar does not ride a recycled view.
        clearListChildFocus(list);
        // Abort fling / leftover PositionScroller from older builds before sticky pin.
        abortListMotion(list);
        setSelectionFromTopSuppressed(list, position, selTop);
        requestFor(list).focusAfterSelect(position, true);
    }

    /**
     * 2026-07-11 — Drop child focus before select so highlight does not ride the old view.
     * Layman: turn off the old glow before the list ticks, then light the new row.
     * Technical: clearFocus on findFocus(); avoids one-frame dual state_focused chrome.
     */
    private static void clearListChildFocus(ListView list) {
        View focused = list.findFocus();
        if (focused != null && focused != list) {
            focused.clearFocus();
        }
    }

    /**
     * 2026-07-11 — Kill leftover fling/smooth scroll so sticky pin is not fought.
     * Layman: stop the list sliding before we park the highlight.
     * Technical: smoothScrollBy(0,0) aborts AbsListView PositionScroller on API 17+.
     */
    private static void abortListMotion(ListView list) {
        try {
            list.smoothScrollBy(0, 0);
        } catch (Exception ignored) {
        }
    }

    /** 2026-07-11 — Arm Y1SafeListView rect-scroll suppress for edge sticky ownership. */
    private static void armRectSuppress(ListView list) {
        if (list instanceof Y1SafeListView) {
            ((Y1SafeListView) list).setSuppressChildRectScroll(true);
        }
    }

    /**
     * 2026-07-11 — setSelectionFromTop with Y1SafeListView rect-scroll suppressed for the call.
     * Focus path keeps suppress until requestFocus finishes (see ListFocusRequest).
     */
    private static void setSelectionFromTopSuppressed(ListView list, int position, int selTop) {
        armRectSuppress(list);
        list.setSelectionFromTop(position, selTop);
        // Suppress stays on until focusAfterSelect.requestFocus (finishRectSuppress).
    }

    /**
     * 2026-07-11 — Compatibility entry; old name bottom-aligned every tick.
     * Now delegates to ensureChildVisible (edge-only). Reversal: restore bottom-align body.
     */
    public static void scrollToChildBottom(ScrollView scroll, View child) {
        ensureChildVisible(scroll, child);
    }

    private static ListFocusRequest requestFor(ListView list) {
        synchronized (REQUESTS) {
            ListFocusRequest request = REQUESTS.get(list);
            if (request == null) {
                request = new ListFocusRequest(list);
                REQUESTS.put(list, request);
            }
            return request;
        }
    }

    private static final class ListFocusRequest implements Runnable {
        private final ListView list;
        private int position;
        private boolean retry;
        /** 2026-07-11 — True when edge sticky path left suppressChildRectScroll armed. */
        private boolean clearRectSuppress;

        ListFocusRequest(ListView list) {
            this.list = list;
        }

        void begin(int position, boolean clearRectSuppress) {
            main().removeCallbacks(this);
            // 2026-07-11 — Drop prior edge suppress if this request is mid-viewport walk.
            if (this.clearRectSuppress && !clearRectSuppress) {
                finishRectSuppress();
            }
            this.position = position;
            this.clearRectSuppress = clearRectSuppress;
            retry = false;
        }

        /**
         * 2026-07-11 — After setSelectionFromTop; only requestFocus on the row.
         * {@code edgeSticky} keeps rect-scroll suppressed through the focus handoff.
         */
        void focusAfterSelect(int position, boolean edgeSticky) {
            begin(position, edgeSticky);
            if (edgeSticky && list instanceof Y1SafeListView) {
                ((Y1SafeListView) list).setSuppressChildRectScroll(true);
            }
            list.post(this);
        }

        @Override public void run() {
            main().removeCallbacks(this);
            // Selection already applied by ensureListPositionVisible; only focus the row.
            View child = list.getChildAt(position - list.getFirstVisiblePosition());
            if (child != null) {
                retry = false;
                // 2026-07-11 — Skip if already focused (avoids second requestChildRectangleOnScreen).
                if (!child.isFocused()) {
                    child.requestFocus();
                }
                finishRectSuppress();
            } else if (!retry) {
                retry = true;
                list.post(this);
            } else {
                finishRectSuppress();
            }
        }

        /** 2026-07-11 — Re-enable requestChildRectangleOnScreen after edge focus lands. */
        private void finishRectSuppress() {
            if (!clearRectSuppress) return;
            clearRectSuppress = false;
            if (list instanceof Y1SafeListView) {
                ((Y1SafeListView) list).setSuppressChildRectScroll(false);
            }
        }
    }
}
