# 025 — Reader panels, scroll navigation, and Find

Accepted for the build-14 Android UAT slice on 2026-11-06. Build 13's physical feedback supersedes emulator-only assumptions about the scroll layout and backward handle extension. About reads the Android package version name and version code, so an installed APK can identify itself offline.

For reflowable EPUB, Scroll uses native WebView vertical panning. A vertical swipe begun at the bottom or top of a chapter moves to the next chapter's start or previous chapter's end. Pages uses horizontal swipes. EPUB chapters are separate rendered resources, so chapter transitions are discrete. Fixed-layout books remain paged. Preserve the session's layout preference and its original EPUB source.

Before an explicit Previous/Next turn or a swipe with a range selected, snapshot the Readium locator and quote into the current session. Keep the passage available from Selection, including Open passage and Clear selection, even while another page is visible. Native handle drags are separate: they extend the current range, retain the opposite endpoint, and stop on release. The action mode tracks resting endpoints before the drag and tolerates adjacent inline nodes at text edges. Extension across distinct chapters remains open.

Selection actions, the notes list, note editing, and saved highlight actions live in a panel inside the reader. It overlays part of the book without changing its page width or scroll offset. Panel content scrolls on narrow screens and when the keyboard is open. Android Back closes it. Share, dictionary and web search still use the system only after the corresponding action is selected; web search asks before sending selected text.

EPUB Find uses the pinned Readium publication search service with local book bytes. It offers contextual excerpts, cancellation, a no-results state, and a capped list of 100 matches. Opening a hit navigates to its source locator and shows a temporary decoration in a separate group. Results are not saved as annotations. Search qualification on large and complex publications remains open.

Keep the existing application ID, signer, SQLite schema 2, and independent annotation storage. The APK remains private. See the build-14 UAT checklist and result ledger for physical acceptance.
