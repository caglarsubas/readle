# Build 14 — Android reader UAT

On your Samsung Z Fold7, open the private `output/android/readle-uat-14.apk` and choose **Update** over build 13. Leave the installed app and its data in place. The APK runs offline and needs no development server or AI key. It uses the same `com.readle.uat` application ID, signing certificate and SQLite schema 2.

Open **About** in the Library header to confirm **Version 0.1.0-spike · Build 14**. Use **Add sample books** or the private `readle-uat-14-fixtures.zip`, then repeat the reader checks in the EPUB that exposed the original issues.

| ID | Action | Expected |
|---|---|---|
| B14-01 | Update over 13, open About and resume an old session. | About shows build 14; existing books, locations, notes and highlights remain. |
| B14-02 | Select Layout → Continuous scroll in a long EPUB chapter. Swipe vertically with and without a selected word. At the bottom, swipe up again. Then switch to Pages and swipe horizontally. | Continuous scroll moves the text up/down and continues to the next chapter at its edge; Pages moves a full page sideways. The selected word remains available. |
| B14-03 | In Pages, long-press a word. Drag the end handle to the right text edge and hold. On a later page, drag the start handle to the left edge and hold. | Each edge turns in its direction while extending the range. The opposite boundary stays fixed. Releasing stops the turn. |
| B14-04 | Select a word and tap Next, then Previous. Repeat with a horizontal swipe. Open Selection and save or reopen the passage. | The same words remain available as the working selection even when you move away. Opening the passage returns to its source. |
| B14-05 | Open Selection → Add note, type and save. Tap the highlight, edit the note, and open Notes. Press Android Back from the panel. | The controls open inside the reader, preserve the reading context and close back to the page. The note is saved in this session. |
| B14-06 | Enable airplane mode. In the sample EPUB, Find `hypothetical linear response` and open the hit. Try a query with no matches. | A preview opens the matched passage in chapter 2 and marks it. A missing phrase shows “No matches found.” |
| B14-07 | Repeat B14-02..06 on the cover and inner screens. Fold/unfold, force-stop and reopen a session with a saved note. | The reader remains usable; the saved source passage and chosen layout survive. |

Fixed-layout EPUBs stay in Pages. In reflowable EPUB, chapter edges are separate transitions; a native range can extend across pages of the same chapter. See [recorded owner feedback](UAT_RESULTS.md) and [build evidence](READER_SPIKE_STATUS.md).

Report device, Android version, fold state and one Pass / Fail / Blocked result per item. For a failure, include the book, page/chapter, expected behavior and what you saw.
