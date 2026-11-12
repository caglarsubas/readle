# Readle's pinned Readium navigator patch

This module compiles Readium Kotlin navigator 3.1.2 at upstream commit `71074ea0c424eabbc39a93b48a4cefe366f7741b`, with only the changes in `patches/readium-selection.patch`. Shared/streamer APIs and the navigator's runtime dependency versions stay at the existing pinned versions. The upstream build's publishing infrastructure is replaced by this small Android library build.

`scripts/prepare_readium.py` downloads the immutable source archive, verifies SHA-256 `0fb071a8a9966eb41a22065e3a38d629502b38f1394e36f461cd0129835051ae`, extracts only the navigator module and applies the patch. It caches the archive under `~/.cache/readle`; generated sources are under this module's ignored `build/upstream` directory. A clean build requires network access for this archive and normal Maven dependencies. Cached builds do not need to fetch it again. A checksum/patch mismatch fails the build.

The change belongs inside the navigator because its internal `R2BasicWebView.onOverScrolled` discards scrolling whenever selection is active. Updating to a newer legacy navigator alone does not remove that guard. The app does not reflect into private fields or modify a Maven-cache binary. Readium selection, locator, annotation and fragment APIs remain in use.

The Readium BSD-3-Clause license is retained in source headers and copied into the distributed assets as `readle_readium_LICENSE.txt`. The upstream PhotoView jar is retained from the verified archive. See `docs/THIRD_PARTY_NOTICES.md`. Upstream references: https://github.com/readium/kotlin-toolkit/tree/71074ea0c424eabbc39a93b48a4cefe366f7741b and https://github.com/readium/kotlin-toolkit/issues/325.

`ReadleSelectionActionMode` delegates the platform menu/handles and monitors their active drag interval. It polls the changing native DOM range boundaries to scroll continuously at top/bottom edges or turn complete Readium pages at left/right edges, then stops when the action mode is finished or the range/view disappears. It preserves the original column-scrolling workaround. Native gesture tests cover finger scrolling, vertical handle auto-scroll, both page directions, stopping on release and saving/reopening the rendered range.


Build 14 routes ordinary vertical scroll gestures to WebView, recognizes chapter-edge swipes on the vertical axis, and lets the app capture a selected source passage before an ordinary page turn. The handle monitor compares against the resting range and tolerates nearby inline boundaries. See [ADR 025](../../../../docs/adr/025-reader-panels-and-page-navigation.md).


New selections also store source node paths and offsets. Preparation adds the readable `patches/readium-selection-anchors.js` helper to the verified reflowable bundle and hooks its shared range resolver at one exact signature. The resolver verifies the original quote before rendering or navigating, and leaves legacy quote locators intact. Preparation fails if that pinned signature changes.

Build 15 retains the live native range during ordinary button/swipe navigation. Selected paginated turns use the same native pager as held-edge extension, because the JavaScript scroll path is guarded while selecting. Returning to the source page leaves the handles adjustable. The guard, continuous-scroll path, and resource-boundary fallback stay in place. See [ADR 026](../../../../docs/adr/026-live-selection-page-turns.md).

Build 16 preserves the turn snapshot when WebView drops the live selection. The source resolver restores adjustable grips on return, and selected vertical edge swipes use the chapter pager directly. See [ADR 027](../../../../docs/adr/027-resume-adjustable-epub-selection.md).
