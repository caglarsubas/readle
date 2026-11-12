# 026 — Keep native selections adjustable during page navigation

Accepted 2026-11-07 for Android build 15, following VIS14-01. This refines ADR 025: retaining a quote in the session does not replace an active, adjustable selection.

Ordinary Previous/Next buttons and horizontal swipes snapshot the selected locator and quote without clearing Chromium's native range. Within one paginated EPUB resource, selected turns use Readium's existing native pager, as held-edge extension already does. The pager updates the reading location while preserving the native selection and its handles. Returning to a page containing an endpoint permits immediate dragging without another long press. Handle movement must keep the opposite DOM endpoint fixed and stop turning after release.

The normal JavaScript pagination path is intentionally guarded while selection is active to avoid Readium's CSS-column selection issue. Removing that guard would broaden the change and revive the underlying scrolling problem. The focused navigator patch instead routes only active, paginated selections through the native pager, respecting reading direction and resource boundaries. Unselected and continuous-scroll navigation retain their existing paths. Crossing a chapter still saves the quote; an active native range across distinct resources remains outside this slice.

Re-capturing the same quote at the same source endpoints retains the original complete locator, independently of viewport progression. Readium 3.1's `Locations.fromJSON` removes known fields from its input object, so parsing for rendering or navigation receives a defensive JSON copy. The durable selection remains intact. The pre-existing full-locator equality regression caught both the progression drift and parser mutation; its assertion is retained.

Selection actions may explicitly finish the live range when opening their panel. Clear selection and annotation actions retain their existing behavior. Session snapshots, source endpoint verification, annotation ownership, SQLite schema 2, and the UAT signing identity remain unchanged.

Regression evidence must include a native long press, ordinary navigation away and back, and a real handle drag afterward. Compare the exact DOM paths and offsets before/after ordinary navigation and the opposite endpoint after extension. A DOM-created range, retained quote, or screenshot alone is insufficient. Repeat button and swipe paths on the foldable cover and inner screens, with Android Studio visual observation. See the build-15 UAT report for results; physical owner acceptance remains separate.

Coordinate-driven handle tests wait for WebView's visual-state callback, the next frame and native UI idle after returning to the page. A JavaScript scroll offset can update before the native handle positions are drawn. Normal regression runs do not use the optional multi-second visual-observation pauses.

Build 15 physical feedback exposed range loss on page changes. [ADR 027](027-resume-adjustable-epub-selection.md) adds restoration when WebView loses the live range.
