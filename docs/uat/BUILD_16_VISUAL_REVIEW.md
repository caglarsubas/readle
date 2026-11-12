# Build 16 — UAT 15 reader follow-up

2026-11-12. The [owner report](UAT_RESULTS.md#build-15--owner-feedback--2026-11-12) fails B15-02 and B15-04 on the Samsung Z Fold7 / Android 17. Other behavior was reported working. The Add sample books concern was tentative.

## Reproduction and changes

- **B15-02:** The unchanged Build 15 APK failed the new [chapter return regression](evidence/build-16/build15-baseline.txt). After a real long press near the end of chapter 1, Next and Previous returned to the chapter but the live range was empty. Build 16 keeps the exact source snapshot through the turn and restores draggable grips if WebView discards its range. Restoration verifies the resource, endpoints, quote and visibility. It no longer depends only on the debounced reading locator. See [ADR 027](../adr/027-resume-adjustable-epub-selection.md).
- **B15-04:** The selected vertical chapter swipes passed in both directions on the Build 15 cover emulator baseline. The Samsung failure was not reproduced there. Build 16 keeps selected touch streams in the active WebView and turns directly through the chapter pager from a recognized edge gesture. Tests cover edge swipes with selected text, native handle auto-scroll, and adjustment after a continuous-scroll chapter return.
- **Samples:** The packaged Build 15 assets were present. Adding existing samples had no visible confirmation next to the action. Build 16 returns the two current import results and shows each format's outcome beside the button, with links to the corresponding books. Duplicate content keeps the same book ID and sessions.

The first restoration attempts exposed three distinct problems: a collapsed caret prevented `addRange`, a quick round trip did not emit a new debounced reading locator, and a fixed-position layer moved offscreen with Readium's CSS columns. Those attempts did not qualify. The unfolded visual check also exposed a stale pale marker when a new selection replaced the old chapter’s selection. Markers now use resource-scoped IDs, and the edge-swipe test requires the old marker to disappear. The final fallback clears the caret, checks the source after the native turn, and positions grips in document coordinates.

## Artifact and evidence

- Standalone private APK: `output/android/readle-uat-16.apk`, `com.readle.uat`, `0.1.0-spike`, version code **16**, min SDK 24, target SDK 36, SQLite schema 2.
- APK SHA-256 `92a99dd630165aee66b660e35b72b502deecb80c0b78fe364c7f7a13d9492f30`.
- UAT signer SHA-256 `0a6ed121b47b392614698fa2b9e20555c43f8990dcf9ca3270b597fc8163cea5`, unchanged. Use **Update** to retain app data. Fixtures: `readle-uat-16-fixtures.zip`.
- Pixel Fold API 36 / Android 16, cover 1080 × 2092 and inner 2208 × 1840. Android Studio Running Devices provides visual observation; owner-authorized ADB and instrumentation supply precise touch input.
- [Cover fallback run before the final marker-ID change](evidence/build-16/cover-recovered.txt): native selection, simulated WebView range loss while offscreen, exact restoration, held-edge drag, unchanged opposite endpoint, stable release and save. [Returned grips](evidence/build-16/cover-range-returned.png) · [Extended selection](evidence/build-16/cover-range-adjusted.png).
- [Full cover suite](evidence/build-16/cover-before-marker-encoding.txt): **28 passed** without visual pauses on the candidate with resource-scoped marker IDs. The final artifact changes those IDs to a URL-safe encoding. The [first run](evidence/build-16/cover-first-native.txt) passed 27/28; a PDF highlight retap after note save timed out. [The isolated replay](evidence/build-16/cover-pdf-replay.txt) passed. The final test waits for the keyboard to close and for native UI idle before sampling the tap coordinate. PDF production behavior and assertions are unchanged.
- [Inner screen focused suite](evidence/build-16/inner-focused.txt): **4 passed** on the candidate before the final marker-ID encoding. Covered both selected chapter directions, removal of the prior chapter marker, native and restored upward handle scrolling, and the adjustable range after a chapter round trip. [Returned grips](evidence/build-16/inner-chapter-selection-returned.png) · [Extended page selection](evidence/build-16/inner-chapter-selection-adjusted.png) · [Extended continuous selection](evidence/build-16/inner-scroll-restored-handle-stopped.png).
- [Final APK focused cover retest](evidence/build-16/cover-final-focused.txt): **3 passed** without visual pauses, covering loss and recovery within a chapter, a chapter round trip, selected chapter-edge swipes in both directions and removal of the stale marker.
- [Offline sample import](evidence/build-16/cover-samples.png): observed both “already in your library” results in Android Studio. Each link opened the correct format with its existing session. The installed library already had both samples; a fresh-library import on the physical device was not observed here.
- TypeScript, ESLint, five React Native tests, release/native-test compilation, signature and APK checksum verification passed.

## Owner retest

Run [B16-01..04](ANDROID_UAT.md) on both Z Fold7 screens. Use the EPUB that exhibited the failure alongside the packaged sample. This evidence does not establish physical Android 17 acceptance, selection across EPUB chapters, native iOS, accessibility or named-hardware timing. Rollback uses a schema-2-compatible fix-forward APK with the same signer and a higher version code.
