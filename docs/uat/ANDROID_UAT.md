# Build 13 — EPUB selection gesture UAT

Build 13 fixes the two selection interactions reported in build 12. Application ID is `com.readle.uat`, version code 13, version name `0.1.0-spike`; minimum Android API 24, target 36. It is a signed standalone APK with the same signer and SQLite schema 2 as build 12.

## Update

Open the private `output/android/readle-uat-13.apk` on your Z Fold7 and choose **Update** over build 12. Keep the installed app and its data. Existing books, sessions and annotations remain in place. Built-in sample books and `output/android/readle-uat-13-fixtures.zip` remain available. The build checksum is in [the current receipt](READER_SPIKE_STATUS.md).

USB installation is also available:

```sh
/Users/caglarsubasi/Desktop/prometa/pocs/readle/scripts/install-android-uat.sh 13
```

The owner reported the other build-12 items OK. Those results are retained in [UAT_RESULTS.md](UAT_RESULTS.md). Focus this run on the following gestures, using the self-authored EPUB sample first and then an EPUB you normally read.

## Focused checklist

| ID | Action | Expected result |
|---|---|---|
| B13-01 | Choose Layout → Continuous scroll. Long-press a word. Swipe up/down through the text with the other selection endpoints untouched. | The chapter scrolls while the same word stays selected. Selection → Highlight/Note saves that original word. |
| B13-02 | In continuous layout, select a word and drag the end handle toward the bottom of the reading area. Continue onto text beyond the first screen. | The viewport moves down and the selection grows from its original start. Release and save; the note/highlight returns to the same source words after restart. |
| B13-03 | Choose Layout → Pages. Select a word and drag the end handle toward the right text edge. Hold briefly to turn; keep dragging to the desired ending word. | The page turns while preserving the range's starting word. Holding at the edge can continue turning within the chapter. Release to stop. |
| B13-04 | On a later page, select a word and drag the start handle toward the left text edge. Hold briefly, then extend to an earlier word. | The previous page opens while preserving the range's ending word. The selected span includes text on both pages. |
| B13-05 | After extending a range, lift your finger and wait two seconds. Save a highlight/note, change font size, then force-stop and resume the same session in airplane mode. | Page turning stops on release. The saved range remains at the same original words. A different/new session does not acquire that annotation. |
| B13-06 | Repeat the gestures on the cover and inner screens, including a fold/unfold between reading and resuming a saved range. | The reader remains usable and annotations keep their source anchors. Report any handle drift, unwanted turns, flicker or lost range with exact steps. |

Edge turning uses a brief dwell, like a reader's selection extension; a normal long press alone must not turn the page. The range remains within the current chapter/spine resource. Selecting across separate chapters is still open. EPUB is the primary reader and PDF remains a source/reference view.

```text
Build: 13
Device: Samsung Z Fold7
Android: 17
Installation: Update over build 12
Date:
B13-01..06: Pass / Fail / Blocked, one result per item
Failure details: selected word, layout, direction, fold state, expected/observed
```

Physical-device results remain separate from automated touches on API 36 AVDs. Do not uninstall/clear storage for an update test. Annotation export is not a full library backup. A rollback must retain schema-2 compatibility, the same signer and an increasing version code.
