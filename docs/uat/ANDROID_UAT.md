# Build 16 — Android reader UAT

Open `readle-uat-16.apk` on your Samsung Z Fold7 and choose **Update**. Keep the installed app and its data. The APK uses the same application ID (`com.readle.uat`), signing certificate and database schema as Build 15. It works offline without a development server. In Library → About, confirm **Version 0.1.0-spike · Build 16**.

Repeat the two failed Build 15 checks with the EPUB where you saw the issue. Use both the cover and unfolded screen. **Add sample books** or `readle-uat-16-fixtures.zip` provides the comparison books.

| ID | Action | Expected |
|---|---|---|
| B16-01 | In EPUB **Pages**, long-press a word, turn away, and turn back. Drag a handle without another long press. Try once within the chapter and once from the last page into the next chapter and back. | The same words return as an adjustable selection with grips. Dragging changes the selected words, leaves the opposite endpoint fixed, and stops on release. The extended passage can be highlighted or annotated. |
| B16-02 | In **Continuous scroll**, select text near a chapter edge. Swipe vertically in the text away from the handles, first forward at the bottom, then backward at the top of the next chapter. On the original chapter, also try holding a handle near the top or bottom of the screen. | A swipe from a chapter edge opens the neighboring chapter while the selected passage remains available. Holding a handle scrolls within the chapter and extends the range; releasing stops it. |
| B16-03 | From Library, tap **Add sample books** in airplane mode. Tap **View sample EPUB** and **View sample PDF**. Repeat the import. | Each format reports whether it was added, is already present, or failed. Each available sample opens its existing book and sessions. Repeating the action does not create duplicate books or sessions. |
| B16-04 | Open an existing session and a new session for the same book. Check Notes, EPUB Find, PDF source reading, and reopening after force-stop. | The existing session restores its own work, the new one is blank, and the previously working reader actions still work. |

A single EPUB selection is scoped to one chapter. Crossing into another chapter keeps the source passage; returning to the source restores its grips if WebView discarded them. Report the item, result, book, screen and whether a chapter boundary was crossed.

[Build 15 checklist](BUILD_15_CHECKLIST.md) · [Owner feedback](UAT_RESULTS.md) · [Build 16 verification](BUILD_16_VISUAL_REVIEW.md)
