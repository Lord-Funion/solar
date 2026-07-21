package com.solar.launcher.flow;

import org.junit.Test;

/**
 * 2026-07-20 — Flow flipped-list edge tip gates (no Android).
 */
public class FlowListEdgeHintPolicyTest {

    @Test
    public void noHintUntilUserScrolled() {
        if (!"NONE".equals(FlowListEdgeHintPolicy.edgeHint(false, true, false))) {
            throw new AssertionError("landed on top");
        }
        if (!"NONE".equals(FlowListEdgeHintPolicy.edgeHint(false, false, true))) {
            throw new AssertionError("landed on bottom");
        }
    }

    @Test
    public void topAndBottomAfterScroll() {
        if (!"TOP".equals(FlowListEdgeHintPolicy.edgeHint(true, true, false))) {
            throw new AssertionError("top");
        }
        if (!"BOTTOM".equals(FlowListEdgeHintPolicy.edgeHint(true, false, true))) {
            throw new AssertionError("bottom");
        }
        if (!"NONE".equals(FlowListEdgeHintPolicy.edgeHint(true, false, false))) {
            throw new AssertionError("mid");
        }
    }

    @Test
    public void atTopBottomHelpers() {
        if (!FlowListEdgeHintPolicy.atTop(0, 5)) throw new AssertionError("top0");
        if (FlowListEdgeHintPolicy.atTop(1, 5)) throw new AssertionError("not top");
        if (!FlowListEdgeHintPolicy.atBottom(4, 5)) throw new AssertionError("bottom");
        if (FlowListEdgeHintPolicy.atBottom(0, 0)) throw new AssertionError("empty");
    }

    @Test
    public void labelsUseArrows() {
        String top = FlowListEdgeHintPolicy.hintLabel(FlowListEdgeHintPolicy.TOP);
        String bot = FlowListEdgeHintPolicy.hintLabel(FlowListEdgeHintPolicy.BOTTOM);
        if (top == null || !top.contains("covers") || top.indexOf('\u21BB') < 0) {
            throw new AssertionError("top label " + top);
        }
        if (bot == null || !bot.contains("covers") || bot.indexOf('\u21BA') < 0) {
            throw new AssertionError("bot label " + bot);
        }
        if (!"".equals(FlowListEdgeHintPolicy.hintLabel(FlowListEdgeHintPolicy.NONE))) {
            throw new AssertionError("none");
        }
    }
}
