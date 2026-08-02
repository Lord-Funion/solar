/**
 * 2026-07-15 — Native YouTube backends (Invidious / Piped / YtApiLegacy).
 * Layman: Solar talks to public frontends itself — no notPipe APK or Xposed bridge.
 * Technical: patterns adapted from notPipe 0.3.0 (gohoski, WTFPL); SolarHttp + org.json.
 * Reversal: restore NotPipeClient IPC + SolarNotPipeBridge; delete this package.
 */
package com.solar.launcher.youtube.api;
