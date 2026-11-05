# Install build 12 and run Android UAT

Build 12 is a privately supplied, signed standalone APK for Android 7.0/API 24 or newer. Application ID is `com.readle.uat`, version code 12, version name `0.1.0-spike`. It runs without a development server or AI key. EPUB is the primary reader; PDF is an original-source reference view.

## Update the installed app

1. Transfer `output/android/readle-uat-12.apk` from the development Mac to your Z Fold7, as you did for build 11.
2. Open the APK in My Files and choose **Update**. Keep the installed app and its storage so existing work survives. The same signing identity is used.
3. The built-in **Add sample books** action provides the self-authored EPUB/PDF fixtures. `output/android/readle-uat-12-fixtures.zip` also contains those files, an exact PDF duplicate and an intentionally corrupt PDF for import regression checks.

The checksum and signer fingerprint are in [the build receipt](READER_SPIKE_STATUS.md). If Android requests permission to install from My Files, its [installation guidance](https://support.google.com/pixelphone/answer/7391672?hl=en) explains the setting.

USB installation remains available with one authorized physical phone connected:

```sh
/Users/caglarsubasi/Desktop/prometa/pocs/readle/scripts/install-android-uat.sh 12
```

## Existing notes after the update

Builds 10/11 stored annotations at book level and did not record which session created them. The upgrade preserves that collection as **independent copies in every existing session**. Editing/deleting a copy now affects only its own session. Old orphaned notes, if any, appear in a **Recovered notes** session. All sessions created after the upgrade start empty. Books, source files, session IDs and reading positions remain intact. PDF viewing changes to fit-width and no longer restores build 11's horizontal magnification.

## Build 12 feedback checklist

Use Samsung Z Fold7 / Android 17. Record the APK build and test date. Use sample books first.

| ID | Action | Expected result |
|---|---|---|
| B12-01 | Install over build 11. Open each pre-existing session and its Notes list. | Old source files, session IDs, page/chapter positions and notes remain available. Previously shared notes are retained as independent copies in those existing sessions. |
| B12-02 | For both EPUB and PDF, create a new session for a book with old notes/highlights. | The new session opens at the beginning with no historical highlights, notes or selection. Notes has no old entries. Existing sessions still retain their work. |
| B12-03 | Create a highlight/note in new session A. Create new session B for that book. Add a different note in B, then edit/delete the note in A. Force-stop and reopen both in airplane mode. | B starts blank. A and B retain only their own notes, locations and settings. Editing/deleting in A cannot affect B. |
| B12-04 | In EPUB choose **Layout → Continuous scroll**. Long-press a word, drag the end handle down while the chapter scrolls beyond the initial screen, then Highlight or Note. Change text size, close and reopen the session. Also try **Layout → Pages** and reopen. | A range spanning multiple screens within a chapter is saved at the same original words. Its object actions work. The chosen layout and font size restore for that session. Selecting across separate chapters is not supported in this increment. |
| B12-05 | In PDF, long-press a word. Short-tap a blank area outside the selected words, without choosing another word. Repeat beside an existing saved highlight. | Temporary blue selection/handles clear and Selection asks for a new passage. Saved gold highlights and notes remain. Tapping a gold highlight still opens its own object menu. |
| B12-06 | Open PDF page 2. Fold/unfold repeatedly, rotate, change pages, then force-stop/reopen. | Exactly one original page is shown, fit to the current window width. No duplicate page surfaces or horizontal magnification appear. Page and vertical reading position remain resumable. |
| B12-07 | In PDF choose **Open original**, select an installed local PDF viewer, inspect the file and return. | The chosen viewer gets the original PDF with temporary read access. Returning preserves the Readle session. If no PDF viewer is installed, Readle explains that one is needed. |

PDF pinch zoom, full authoring/reflow and new cross-page PDF selections are deferred under the accepted EPUB-first scope. Existing two-page annotations remain accessible. AI/learning and native iOS integration retain their separate milestones.

## Send results

```text
Build: 12
Device: Samsung Z Fold7
Android: 17
Install: update over build 11
Date:
B12-01..07: Pass / Fail / Blocked, one result per item
Failure details: steps, expected result, observed result, exact error
```

Physical-device results are recorded separately from automated tests and emulator fold checks. The current export covers session annotations, not a complete library backup. Schema 2 cannot be opened by a schema-1 binary; rollback requires a compatible fix-forward build using the same signer and a higher version code.
