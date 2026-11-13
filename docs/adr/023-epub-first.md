# 023 — EPUB-first reading, PDF source/reference

Accepted 2026-11-05. After build 11 UAT, the owner selected **EPUB-first; PDF for source/reference** and confirmed the cross-page selection problem also occurs in EPUB.

Reflowable EPUB is the primary reading and learning format for MVP. It receives font/layout controls, source-anchored annotations and the first learning journey. New sessions default to continuous vertical scrolling so an ordinary text selection can extend beyond a screen/page boundary within a chapter. **Layout** switches between continuous scroll and pagination; the preference belongs to the session. Existing sessions without a layout setting retain pagination until the reader chooses otherwise. Selections crossing separate EPUB spine resources/chapters require a further navigator capability and are not claimed by this increment.

PDF import, immutable files, original-page reference viewing, existing annotations and later source/citation previews remain supported. The in-app PDF reference view always fits the current window width and uses one stable page surface through resize/fold events. It retains page and vertical position. Tap outside a transient selection clears it without deleting a saved highlight. **Open original** grants temporary read access to a local Android PDF viewer chosen by the reader. No upload or conversion is performed by that action.

The failed custom PDF magnification/spread/cross-page-authoring path is removed from the MVP reader gate. Build 12 no longer advertises pinch/double-tap PDF zoom or the explicit cross-page selection extension. Existing multi-page annotations remain viewable at their original anchors. Advanced PDF authoring/reflow is a separate, optional P1 qualification against a mature native implementation. PDF-to-learning conversion is deferred beyond the first EPUB learning journey; conversion must preserve equations, figures, reading order and source mapping before use. The original source is never replaced by an unverified conversion.

The original v1.3 document is retained as the historical blueprint; this decision and ADR 022 are explicit user-approved scope overrides. EPUB-first does not imply a fixed-layout EPUB has reflow semantics. Native iOS EPUB integration and physical iOS evidence remain required.

References: [W3C EPUB 3.3](https://www.w3.org/TR/epub-33/) and [Adobe PDF reflow guidance](https://helpx.adobe.com/uk/acrobat/using/reading-pdfs-reflow-accessibility-features.html).
