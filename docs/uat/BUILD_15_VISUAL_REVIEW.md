# Build 15 — adjustable selection after page turns

Engineering verification performed 2026-11-07 using Android Studio on macOS, owner-authorized ADB controls and native touch instrumentation. Physical Samsung Z Fold7 / Android 17 acceptance is not inferred from emulator results.

## Qualified engineering artifact

- Standalone APK: private `output/android/readle-uat-15.apk`, application ID `com.readle.uat`, version `0.1.0-spike`, version code **15**, min SDK 24, target SDK 36.
- APK SHA-256: `06e2f7bfd090d8f0367dbb560945ecc7b919b5a215cb97bd03aab7743e7dc794`.
- Same UAT signing certificate as builds 13/14: SHA-256 `0a6ed121b47b392614698fa2b9e20555c43f8990dcf9ca3270b597fc8163cea5`.
- SQLite schema **2**, no migration. Install using **Update**, retaining application data. Fixtures: private `readle-uat-15-fixtures.zip`, containing the sample EPUB, PDF, duplicate PDF and corrupt import fixture.

## Change and acceptance boundary

Build 14 saved the selected quote and then cleared the live native range before an ordinary page turn. That removed its adjustment handles. Simply retaining the range revealed that Readium guards JavaScript column scrolling during selection. Build 15 keeps the range and routes selected paginated turns through the existing native pager, preserving that guard. See [ADR 026](../adr/026-live-selection-page-turns.md).

The complete source locator also remains unchanged when the native range is unchanged, even while the reading page moves. Readium's locator parser receives a defensive copy because its `Locations.fromJSON` consumes known JSON fields. Rendering and Open source cannot strip those fields from the reader's saved selection.

The two new regressions fail on the unchanged build-14 APK: both button and swipe turns clear the native selection. Build-15 tests require the original DOM endpoints after navigating away/back, an actual native handle drag afterward, the exact opposite endpoint remaining fixed, and stable page offset/quote for 1.4 seconds after release. A retained quote alone cannot pass them. The original complete-locator equality assertion remains in place.

Cross-page native selection remains scoped to one chapter/resource. Chapter transitions preserve the saved quote; native selection across separate resources remains open. Fixed-layout EPUB and PDF scope are unchanged.

## Visual method and devices

Android Studio's existing Running Devices panel displays the installed app. Its unrelated pre-existing IDE project is left intact. ADB and the test harness deliver precise long presses, swipes and handle dwell/release. Opt-in checkpoints leave the actual UI visible for observation and save native guest screenshots. Assertions inspect the rendered native selection, source endpoints, viewport and SQLite state.

- Pixel Fold API 36 / Android 16, `emulator-5554`: cover **1080 × 2092**, inner **2208 × 1840**. Both button and swipe round trips are followed by native handle extension in opposite directions.
- Pixel 7 API 36 / Android 16, `emulator-5556`: existing sample book with Session 2 in chapter 1 and Session 1 in chapter 2. The saved note `rest` on `appearance` and Pages preference are inspected before/after in-place updates and offline force-stop/reopen.
- Both devices use airplane mode with Wi-Fi disabled. The APK requires no development server. Installed APK hashes are checked against the supplied file.

The development sequence includes unpublished build-15 candidates. The phone moves from build 14 to a candidate, then receives the final build-15 APK in place. Retention evidence covers that update chain; it is not a claim of a separate clean 14 → final-15 installation experiment. Owner UAT should repeat B15-01 on the installed physical app.

## Verification ledger

| Check | Status |
|---|---|
| Build-14 native regression baseline | Both new tests fail because page navigation clears the live selection |
| Build-15 source-locator regression | Pass: complete locator equality, note save and Open passage |
| Native handle checks, inner/cover | 2 passed per screen with visual-observation checkpoints on the final APK |
| Full native suite on Pixel 7 | **23/23 passed**, 206.516 seconds, final APK and synchronized native input |
| Rendered-frame/native-idle synchronization | **Three consecutive replays passed** after recovery, without multi-second visual pauses |
| Storage and migration on final APK | 9 passed on Pixel Fold API 36 |
| TypeScript, ESLint, React Native tests | Pass; 4 React Native tests |
| Release APK and native test compilation | Pass, Java 21 |
| Signature, About, retained sessions/note | Same signer; About native test passed. Final-APK offline restart visibly retained two phone sessions and the saved note. Cover → inner → cover retained foldable notes and layout |

The initial range-retention candidate could not turn pages through the guarded JavaScript path. A broader candidate run then caught viewport progression drift and the consuming JSON parser. Those attempts remain in local `output/android/build-15/` alongside their superseding final runs. They are not counted as final passes.

The immediate synthetic swipe-to-handle case is timing sensitive. Capturing a screenshot before the drag made it pass; a DOM scroll offset alone did not establish native input readiness. The regression now awaits WebView's visual-state callback, a rendered frame and 500 ms of native accessibility idle, while retaining the real touch drag and endpoint assertions. It uses no multi-second observation pause in normal runs. After recovery, three consecutive replays passed without the optional visual pauses, followed by all 23 native tests. The earlier 22/23 run is retained as pre-synchronization history.

At 21:31 local time the Pixel 7 host emulator exited during an early repeat. Its macOS report identifies `qemu-system-aarch64`, `EXC_BAD_ACCESS` and the emulator hang detector. The Mac subsequently locked. After the owner manually unlocked it, the AVD was cold booted without clearing data. A System UI startup ANR overlaid the first replay; choosing Wait recovered the system. That failed setup attempt is retained separately from the subsequent three passing replays and 23/23 suite. A native-pan cancellation experiment did not resolve the fast-input case and was reverted; it is absent from the APK.

On the final APK, an actual phone force-stop/offline reopen visibly showed both original sessions and the `appearance` / `rest` note in Pages. The foldable retained its `appearance` / `Fold visual UAT edited` note, saved source highlights, open Notes panel and Pages choice through cover → inner → cover. At 21:54 the Pixel 7 host emulator exited again during an additional UI dump/screenshot, after the suite and visible note restoration had completed. Its second macOS report again points to the host hang detector. The extra note PNG and a final About revisit were not captured; the native About test, earlier update-chain About image and final APK metadata remain available. Neither host interruption is treated as a Readle crash or a successful test.

**Build 15 passes engineering qualification for owner UAT.** Physical Samsung Z Fold7 / Android 17 acceptance remains pending.

## Saved evidence

- Source range and live handles after returning: [inner buttons](evidence/build-15/inner-buttons-range-returned.png), [inner swipes](evidence/build-15/inner-swipes-range-returned.png), [cover buttons](evidence/build-15/cover-buttons-range-returned.png), [cover swipes](evidence/build-15/cover-swipes-range-returned.png).
- Subsequent native adjustment: [inner forward](evidence/build-15/inner-buttons-range-adjusted.png), [inner backward](evidence/build-15/inner-swipes-range-adjusted.png), [cover forward](evidence/build-15/cover-buttons-range-adjusted.png), [cover backward](evidence/build-15/cover-swipes-range-adjusted.png).
- [Inner handle run](evidence/build-15/inner-handles-final.txt), [cover handle run](evidence/build-15/cover-handles-final.txt), [complete-locator regression](evidence/build-15/locator-stability-final.txt), [nine storage/migration checks](evidence/build-15/storage-final.txt).
- Superseded attempts retained: [22/23 phone run before input synchronization](evidence/build-15/phone-native-final.txt), [first synchronized replay passed](evidence/build-15/phone-native-idle-swipe-1.txt), [second replay interrupted](evidence/build-15/phone-native-idle-swipe-2.txt), [emulator interruption summary](evidence/build-15/emulator-interruption.json).
- Update-chain states, captured before the final parser-copy build: [sessions before](evidence/build-15/phone-sessions-before.png), [sessions after](evidence/build-15/phone-sessions-after.png), [note before](evidence/build-15/phone-note-before.png), [note after updates](evidence/build-15/phone-note-after-updates.png), [About](evidence/build-15/about.png).
- Final runs: [all 23 native tests](evidence/build-15/phone-qualified-native.txt), [replay 1](evidence/build-15/phone-verified-swipe-1.txt), [replay 2](evidence/build-15/phone-verified-swipe-2.txt), [replay 3](evidence/build-15/phone-verified-swipe-3.txt), [startup ANR setup failure](evidence/build-15/phone-resumed-swipe-1.txt).
- Final-APK restoration: [cover note](evidence/build-15/cover-preserved-final.png), [unfolded note](evidence/build-15/inner-preserved-final.png), [return to cover](evidence/build-15/cover-return-final.png), [two phone sessions after restart](evidence/build-15/phone-sessions-final.png). The final phone note was observed live; its additional PNG was interrupted as described above.
- [TypeScript](evidence/build-15/typecheck.txt), [ESLint](evidence/build-15/lint.txt), [React Native tests](evidence/build-15/react-native-tests.txt).
- [Hashes and qualification receipt](evidence/build-15/receipt.json); [build-14 failing baseline](evidence/build-15/build14-selection-baseline.txt).

## Handoff and rollback

Run [B15-01..07](BUILD_15_CHECKLIST.md), especially B15-02 and B15-03, on both physical Z Fold7 screens and an EPUB that reproduced the issue. Record device, Android version, fold state and expected/observed results independently of engineering checks.

Physical owner UAT, cross-chapter native ranges, iOS, accessibility, large-book coverage and named-hardware p95 measurements remain open. The next release gate remains evidence based; this slice does not complete Phase A or unlock broad AI-course work by itself. Rollback uses a schema-2-compatible fix-forward APK with the same signer and a higher version code. Keep app data installed.

On 2026-11-12 the owner reported B15-02 and B15-04 failures and a possible sample-import issue. See [the physical record](UAT_RESULTS.md#build-15--owner-feedback--2026-11-12).
