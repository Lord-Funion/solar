package com.solar.launcher.phone;

import android.os.Build;

/**
 * 2026-07-20 — Detects MediaTek SoCs for FM phone gating.
 * Non-MTK phones: block FM with an explanation. MTK phones: warn then allow.
 * Was: FM tried the same path on every device. Now: chrome phones get an early gate.
 * Reversal: always return allowWithoutWarn — prior openFmFromHome behaviour.
 */
public final class PhoneFmGate {

    public enum Decision {
        /** Not phone chrome — leave FM alone. */
        PASSTHROUGH,
        /** Chrome on non-MTK — show unavailable notice, do not open FM. */
        BLOCK_NON_MTK,
        /** Chrome on MTK — warn, then continue into existing FM path. */
        WARN_THEN_ALLOW
    }

    private PhoneFmGate() {}

    /**
     * 2026-07-20 — Decide FM entry behaviour for the current chrome/hardware mix.
     * @param chromeActive PhoneChromePolicy.active()
     * @param hardware BOARD + HARDWARE concat (lowercased by caller or here)
     * @param manufacturer optional MANUFACTURER/BRAND
     */
    public static Decision decide(boolean chromeActive, String hardware, String manufacturer) {
        if (!chromeActive) return Decision.PASSTHROUGH;
        if (looksLikeMediaTek(hardware, manufacturer)) return Decision.WARN_THEN_ALLOW;
        return Decision.BLOCK_NON_MTK;
    }

    /** Live Build.* convenience for MainActivity / MediaSuiteHost. */
    public static Decision decideLive(boolean chromeActive) {
        String hw = safe(Build.HARDWARE) + " " + safe(Build.BOARD)
                + " " + safe(Build.PRODUCT);
        String manu = safe(Build.MANUFACTURER) + " " + safe(Build.BRAND);
        return decide(chromeActive, hw, manu);
    }

    /**
     * 2026-07-20 — MTK tokens in board/hardware/manu (mt65xx, mt67xx, mediatek).
     * Pure string check for unit tests.
     */
    public static boolean looksLikeMediaTek(String hardware, String manufacturer) {
        String h = hardware != null ? hardware.toLowerCase() : "";
        String m = manufacturer != null ? manufacturer.toLowerCase() : "";
        if (h.contains("mediatek") || m.contains("mediatek")) return true;
        if (h.contains("mtk")) return true;
        // Common MediaTek SoC family prefixes used on Y1/Y2-class and phones.
        if (h.contains("mt65") || h.contains("mt67") || h.contains("mt68")
                || h.contains("mt81") || h.contains("mt67")) return true;
        if (h.contains("mt6572") || h.contains("mt6582") || h.contains("mt6592")
                || h.contains("mt6735") || h.contains("mt6750") || h.contains("mt676")) {
            return true;
        }
        return false;
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
