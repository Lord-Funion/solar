package com.solar.launcher;

/**
 * 2026-07-05 — Shared wheel keyboard engine for in-app and system IME trays.
 * Layman: one carousel of letters/symbols the scroll wheel moves through; center types the pick.
 * Technical: charset index + buffer; used by MainActivity and SolarInputMethodService.
 */
public final class SolarWheelKeyboardController {

    public static final String TOKEN_SPC = "[SPC]";
    public static final String TOKEN_DEL = "[DEL]";
    public static final String TOKEN_CONN = "[CONN]";

    /** Wheel charset — lower, upper, digits, symbols, then SPC/DEL/ENT. */
    public static final String[] CHARSET = {
            "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u",
            "v", "w", "x", "y", "z",
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U",
            "V", "W", "X", "Y", "Z",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "!", "@", "#", "$", "%", "^", "&", "*", "-", "_", "+", "=", ".", "?",
            TOKEN_SPC, TOKEN_DEL, TOKEN_CONN
    };

    /** 2026-07-05 — Bluetooth PIN entry: digits + delete + enter only. */
    public static final String[] PIN_CHARSET = {
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            TOKEN_DEL, TOKEN_CONN
    };

    /** Fired when buffer or index changes — UI refresh hook. */
    public interface Listener {
        void onStateChanged();
        /** User picked [CONN] — host runs submit (Wi‑Fi connect, search, etc.). */
        void onEnterRequested();
    }

    private int index;
    private String buffer = "";
    private boolean ppLongDoCase = true;
    private Listener listener;
    /** Active wheel strip — full CHARSET or PIN_CHARSET for Bluetooth pairing. */
    private String[] charset = CHARSET;

    public SolarWheelKeyboardController() {
        reset();
    }

    /** Switch to digit-only strip for Bluetooth PIN entry (2026-07-05). */
    public void setDigitOnlyMode(boolean digitOnly) {
        charset = digitOnly ? PIN_CHARSET : CHARSET;
        index = 0;
        notifyChanged();
    }

    public String[] getCharset() {
        return charset;
    }

    /** Attach UI / IME host for refresh callbacks. */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Clear buffer and park on first letter. */
    public void reset() {
        index = 0;
        buffer = "";
        ppLongDoCase = true;
        notifyChanged();
    }

    /** Seed text before show (Reach search, Wi‑Fi overlay handoff, etc.). */
    public void setBuffer(String text) {
        buffer = text != null ? text : "";
        notifyChanged();
    }

    public String getBuffer() {
        return buffer;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int idx) {
        if (idx < 0 || idx >= charset.length) return;
        index = idx;
        notifyChanged();
    }

    /** Map [CONN] to display label (e.g. [ENT]). */
    public static String displayChar(String ch, String enterLabel) {
        if (TOKEN_CONN.equals(ch)) return enterLabel != null ? enterLabel : "[ENT]";
        return ch;
    }

    /** Index offset for 5-slot strip (pprev/prev/current/next/nnnext). */
    public static int wrapIndex(int base, int offset) {
        return wrapIndex(base, offset, CHARSET.length);
    }

    public static int wrapIndex(int base, int offset, int len) {
        return (base + offset + len) % len;
    }

    public int wrapActiveIndex(int base, int offset) {
        return wrapIndex(base, offset, charset.length);
    }

    public static String charAtIndex(int idx) {
        return charAtIndex(idx, CHARSET);
    }

    public static String charAtIndex(int idx, String[] cs) {
        if (cs == null || idx < 0 || idx >= cs.length) return "";
        return cs[idx];
    }

    public String charAt(int idx) {
        return charAtIndex(idx, charset);
    }

    /** Wheel up — previous charset slot. */
    public void wheelUp() {
        index = wrapActiveIndex(index, -1);
        notifyChanged();
    }

    /** Wheel down — next charset slot. */
    public void wheelDown() {
        index = wrapActiveIndex(index, 1);
        notifyChanged();
    }

    /** Center / OK — type current token into buffer or submit. */
    public void centerPress() {
        String selected = charset[index];
        if (TOKEN_DEL.equals(selected)) {
            deleteLastChar();
        } else if (TOKEN_CONN.equals(selected)) {
            if (listener != null) listener.onEnterRequested();
        } else if (TOKEN_SPC.equals(selected)) {
            buffer += " ";
            notifyChanged();
        } else {
            buffer += selected;
            if (selected.length() == 1) {
                char ch = selected.charAt(0);
                if (ch >= 'A' && ch <= 'Z') {
                    int lower = KeyboardCharset.lowercaseIndexForChar(ch);
                    if (lower >= 0) {
                        index = lower;
                        ppLongDoCase = true;
                    }
                }
            }
            notifyChanged();
        }
    }

    /** Prev track short — delete one character. */
    public void mediaDelete() {
        deleteLastChar();
    }

    /** Next track short — insert space. */
    public void mediaSpace() {
        buffer += " ";
        notifyChanged();
    }

    /** 2026-07-20 — Play long-press: Capitals ↔ Symbols blocks (was Aa / # label). */
    public void playPauseLongPress() {
        if (ppLongDoCase) {
            int flipped = KeyboardCharset.flipCaseIndex(index);
            index = flipped != index ? flipped : KeyboardCharset.mapToNextCharset(index);
        } else {
            index = KeyboardCharset.mapToNextCharset(index);
        }
        ppLongDoCase = !ppLongDoCase;
        notifyChanged();
    }

    /** True when current slot is [DEL] — MainActivity soulseek auto-username path. */
    public boolean isDeleteTokenSelected() {
        return index >= 0 && index < charset.length && TOKEN_DEL.equals(charset[index]);
    }

    /** Replace buffer entirely (soulseek auto-username clear on first DEL). */
    public void clearBuffer() {
        buffer = "";
        notifyChanged();
    }

    private void deleteLastChar() {
        if (buffer.length() > 0) {
            buffer = buffer.substring(0, buffer.length() - 1);
            notifyChanged();
        }
    }

    private void notifyChanged() {
        if (listener != null) listener.onStateChanged();
    }

    /** Unit-test guard — charset + token mapping invariants. */
    public static void selfCheck() {
        KeyboardCharset.selfCheck();
        if (CHARSET.length != 79) throw new AssertionError("charset len");
        if (PIN_CHARSET.length != 12) throw new AssertionError("pin charset len");
        if (!TOKEN_CONN.equals(CHARSET[CHARSET.length - 1])) throw new AssertionError("CONN last");
        if (!"[ENT]".equals(displayChar(TOKEN_CONN, "[ENT]"))) throw new AssertionError("ENT label");
        SolarWheelKeyboardController c = new SolarWheelKeyboardController();
        c.centerPress();
        if (!"a".equals(c.getBuffer())) throw new AssertionError("type a");
        c.wheelDown();
        if (c.getIndex() != 1) throw new AssertionError("wheel");
        SolarWheelKeyboardController pin = new SolarWheelKeyboardController();
        pin.setDigitOnlyMode(true);
        if (!"0".equals(pin.charAt(0))) throw new AssertionError("pin start");
        pin.centerPress();
        if (!"0".equals(pin.getBuffer())) throw new AssertionError("pin type 0");
    }
}
