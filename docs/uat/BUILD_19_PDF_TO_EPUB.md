# Build 19 PDF to EPUB UAT

Install the signed APK using **Update**. In **About**, confirm Build 19. Use `2510.25819v1.pdf` from your Library, or import it from Downloads.

1. Enable airplane mode and disconnect USB. In **Library**, open the PDF and tap **Convert to EPUB**. The resulting EPUB opens at its book details with zero sessions.
2. Tap **Start reading**. In **Contents**, choose **PDF page 6**. Change the font size, long-press text, save a highlight, and find it under **Notes**. Try both **Scroll** and **Pages** under **Layout**.
3. Tap **Original page**. Check that the viewer opens page 6 of 33, including the original figure. Tap **Back to EPUB** and check that your passage and highlight are still there.
4. Force-stop Readle and open it again offline. Resume the EPUB session and check the saved place and highlight. Open the PDF book details and tap **View EPUB edition**. Verify that the existing EPUB and its session appear. Open the original PDF's walkthrough from its own session list.
5. On the Z Fold, repeat step 3 on the cover and unfolded displays.

The EPUB uses text extracted from the original PDF. Complex layouts can affect reading order and line breaks. The original page supplies figures, tables, and typeset equations. A PDF without selectable text requires an OCR copy.

On the Android Studio Android 16 phone emulator, Build 19 installed over Build 18 and converted the existing 33-page PDF in airplane mode. The converted text supported highlighting, font sizing, and both layouts. **Original page** showed the figure on PDF page 6 and returned to the EPUB. Reopening after force-stop restored page 6, the saved layout, and the highlight. **View EPUB edition** reused the existing EPUB and its session. The original walkthrough remained in the PDF's session list. Eleven native persistence and migration checks passed, including missing-file repair and rejection of a PDF without selectable text. Physical Z Fold verification is pending.
