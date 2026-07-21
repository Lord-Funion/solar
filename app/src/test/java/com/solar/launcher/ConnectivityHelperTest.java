package com.solar.launcher;

import org.junit.Test;

public class ConnectivityHelperTest {
    @Test
    public void itemNeedsInternetForDiscovery_reachAndYoutubeAudio() {
        if (!ConnectivityHelper.itemNeedsInternetForDiscovery(HomeMenuConfig.ID_SOULSEEK)) {
            throw new AssertionError("reach");
        }
        // 2026-07-15 — YouTube Audio home tile hides offline like Get Music.
        if (!ConnectivityHelper.itemNeedsInternetForDiscovery(HomeMenuConfig.ID_YOUTUBE_AUDIO)) {
            throw new AssertionError("youtube_audio");
        }
        if (ConnectivityHelper.itemNeedsInternetForDiscovery(HomeMenuConfig.ID_THEMES)) {
            throw new AssertionError("themes works offline");
        }
        if (ConnectivityHelper.itemNeedsInternetForDiscovery(HomeMenuConfig.ID_PODCASTS)) {
            throw new AssertionError("podcasts not discovery-gated");
        }
        if (ConnectivityHelper.itemNeedsInternetForDiscovery(HomeMenuConfig.ID_MUSIC)) {
            throw new AssertionError("music offline ok");
        }
        if (ConnectivityHelper.itemNeedsInternetForDiscovery(HomeMenuConfig.ID_PC_UPLOAD)) {
            throw new AssertionError("pc upload is local network");
        }
    }

    @Test
    public void itemNeedsInternet_matchesDiscovery() {
        if (ConnectivityHelper.itemNeedsInternet(HomeMenuConfig.ID_PODCASTS)) {
            throw new AssertionError("podcasts action uses requireInternet directly");
        }
        if (!ConnectivityHelper.itemNeedsInternet(HomeMenuConfig.ID_SOULSEEK)) {
            throw new AssertionError("reach");
        }
        if (!ConnectivityHelper.itemNeedsInternet(HomeMenuConfig.ID_YOUTUBE_AUDIO)) {
            throw new AssertionError("youtube_audio");
        }
    }

    @Test
    public void itemNeedsLocalNetwork_pcUploadOnly() {
        if (!ConnectivityHelper.itemNeedsLocalNetwork(HomeMenuConfig.ID_PC_UPLOAD)) {
            throw new AssertionError("pc upload");
        }
        if (ConnectivityHelper.itemNeedsLocalNetwork(HomeMenuConfig.ID_PODCASTS)) {
            throw new AssertionError("podcasts not local-only");
        }
    }

    @Test
    public void shouldShowHomeShortcut_podcastsOfflineWithSaved() {
        if (!ConnectivityHelper.shouldShowHomeShortcut(HomeMenuConfig.ID_PODCASTS, false, false, true)) {
            throw new AssertionError("podcasts with saved offline");
        }
        if (ConnectivityHelper.shouldShowHomeShortcut(HomeMenuConfig.ID_PODCASTS, false, false, false)) {
            throw new AssertionError("podcasts without saved offline");
        }
        if (!ConnectivityHelper.shouldShowHomeShortcut(HomeMenuConfig.ID_PODCASTS, true, true, false)) {
            throw new AssertionError("podcasts online");
        }
    }

    @Test
    public void shouldShowHomeShortcut_themesOffline() {
        if (!ConnectivityHelper.shouldShowHomeShortcut(HomeMenuConfig.ID_THEMES, false, false, false)) {
            throw new AssertionError("themes offline");
        }
    }

    @Test
    public void shouldShowHomeShortcut_reachAndPcUpload() {
        ConnectivityHelper.setReachPeerOk(true);
        ConnectivityHelper.setDeezerEnabled(false);
        ConnectivityHelper.setDeezerLoginOk(false);
        if (ConnectivityHelper.shouldShowHomeShortcut(HomeMenuConfig.ID_SOULSEEK, false, true, false)) {
            throw new AssertionError("reach offline");
        }
        // 2026-07-19 — Null-prefs path: Soulseek OFF; Get Music needs Deezer runtime flags.
        // Was: peer ok alone unlocked Get Music. Reversal: restore peer-only assertion.
        if (ConnectivityHelper.shouldShowHomeShortcut(HomeMenuConfig.ID_SOULSEEK, true, false, false)) {
            throw new AssertionError("reach alone must not unlock Get Music without prefs");
        }
        ConnectivityHelper.setDeezerEnabled(true);
        ConnectivityHelper.setDeezerLoginOk(true);
        if (!ConnectivityHelper.shouldShowHomeShortcut(HomeMenuConfig.ID_SOULSEEK, true, false, false)) {
            throw new AssertionError("deezer runtime unlocks Get Music when prefs null");
        }
        ConnectivityHelper.setDeezerEnabled(false);
        ConnectivityHelper.setDeezerLoginOk(false);
        ConnectivityHelper.setReachPeerOk(false);
        if (ConnectivityHelper.shouldShowHomeShortcut(HomeMenuConfig.ID_SOULSEEK, true, false, false)) {
            throw new AssertionError("reach peer blocked");
        }
        ConnectivityHelper.setReachPeerOk(true);
        if (ConnectivityHelper.shouldShowHomeShortcut(HomeMenuConfig.ID_PC_UPLOAD, true, false, false)) {
            throw new AssertionError("pc upload needs lan");
        }
        if (!ConnectivityHelper.shouldShowHomeShortcut(HomeMenuConfig.ID_PC_UPLOAD, false, true, false)) {
            throw new AssertionError("pc upload on lan");
        }
    }

    @Test
    public void shouldShowMenuItem_nullId() {
        if (ConnectivityHelper.shouldShowMenuItem(null, null)) {
            throw new AssertionError("null id");
        }
    }
}
