# Build 15 — Android reader UAT

Engineering checks passed: 23 native tests, three consecutive swipe-to-handle replays, and Android Studio visual review on both foldable screens. Physical owner acceptance is pending; see [the results and evidence](BUILD_15_VISUAL_REVIEW.md).

Open the privately supplied `readle-uat-15.apk` on your Samsung Z Fold7 and choose **Update** over the existing Readle UAT app. Keep the app and its data installed. Build 15 uses the same `com.readle.uat` application ID, signing certificate and SQLite schema 2 as builds 13/14. It runs without a development server or AI key.

Open Library → **About** and confirm **Version 0.1.0-spike · Build 15**. Use an existing EPUB plus **Add sample books** or `readle-uat-15-fixtures.zip`. The sample's repeated paragraphs are intentional.

This build addresses VIS14-01: ordinary page turns retain the live native selection, so its handles can be dragged after returning to the source page. The exact selected source locator stays separate from the current reading page. See [ADR 026](../adr/026-live-selection-page-turns.md) and [Build 15 evidence](BUILD_15_VISUAL_REVIEW.md). Owner results on the physical Z Fold7 / Android 17 remain separate from emulator checks.

| ID | Action | Expected |
|---|---|---|
| B15-01 | Update the installed app, open About, and resume an old session. | About shows build 15. Existing books, distinct sessions, notes, highlights and layout choices remain. |
| B15-02 | In Pages, long-press a word. Tap Next, then Previous. Drag the returned selection's end handle to the right text edge and hold; release it. | The same word remains selected with usable native handles. The range extends onto the next page, its starting boundary stays fixed, and turning stops after release. No second long press is needed. |
| B15-03 | On a later page, select a word. Swipe to the previous page and then back. Drag the selection's start handle to the left text edge and hold; release it. | The same range and handles return. Extension moves backward, its ending boundary stays fixed, and turning stops after release. |
| B15-04 | In Continuous scroll, select a word and swipe vertically. At a chapter edge, swipe again to cross it. Switch to Pages and swipe horizontally. | Scroll moves text vertically while retaining the selected word. Chapter-edge navigation works in both directions. Pages moves horizontally. |
| B15-05 | Open Selection → Add note, save, edit the saved note, and use Open source. Open Notes and press Android Back. Then explicitly Clear selection and select another word. | Actions stay inside the reader; edits remain on the original source range. Back closes Notes. Clear selection allows a fresh range without bringing back the old one. |
| B15-06 | In airplane mode, Find `hypothetical linear response` in the sample EPUB and open the hit. Try an absent phrase. | The hit opens chapter 2 and marks the exact phrase. An absent phrase shows “No matches found.” |
| B15-07 | Repeat B15-02..06 on cover and inner screens. Save a long highlight, force-stop, reopen through Library, and fold/unfold. Open another session for the same book. | Saved source passages, notes and layout survive. Each session keeps its own workspace; a new session starts blank. |

Native selection extension is supported across pages of one chapter/resource. Cross-chapter native ranges remain open; chapter transitions preserve the saved quote. Fixed-layout EPUB stays in Pages. PDF remains source/reference under the accepted EPUB-first scope.

Report Pass / Fail / Blocked per item, fold state, book and expected versus observed behavior. Physical-device, iOS, accessibility and named-hardware timing gates remain independent.

Previous checklist: [Build 14](BUILD_14_CHECKLIST.md). Earlier observations: [Build 14 follow-up](BUILD_14_VISUAL_FOLLOWUP.md).
