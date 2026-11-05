# 001 — Native reader spike

Status is experimental. **2026-11-05 override:** [ADR 023](023-epub-first.md) supersedes the full PDF reader spike scope; PDF is now a fit-width source/reference view and EPUB is the primary reader. [ADR 022](022-session-annotations.md) supersedes book-shared annotations. The remaining paragraphs describe the initial spike for historical context. React Native 0.83.1 carries the library/detail/session UI. Android invokes a native ReaderActivity through a typed bridge. Readium Kotlin 3.1.2 provides EPUB navigation, source locators, selection and decorations. Original PDF pages are rendered by Android PdfRenderer. PDFBox Android 2.0.27.0 supplies page-space glyph geometry and extracted text, with immutable source hashes, quote text, page indices and rectangles stored in SQLite.

Readium's advertised PDF support does not establish highlighting/selection parity. The PDF adapter therefore has its own selection and annotation path. The spike uses endpoint dragging and an explicit Extend to next page action. It does not yet implement a continuous cross-page drag or pinch zoom. Zoom controls preserve PDF-point annotation geometry. EPUB uses its native text selection and reflow preferences.

The Android UAT app ID is com.readle.uat, minimum API 24, target API 36. Initial supported files are unencrypted text PDF and static EPUB. Imports have a 128 MiB compressed/expanded limit and 4096-entry limit. Scripted and externally loaded EPUB content is rejected in the spike. Real-world format fidelity, accessibility, performance and parsing isolation require further tests before Phase A/B acceptance.

The template's Gradle 9 wrapper conflicted with the supplied toolchain resolver. Gradle 8.14.3 matches the template's Android Gradle Plugin 8.12 baseline and Kotlin 2.1.20. Java 21 is used locally. Library versions and package-lock are pinned for reproducible checks.

The generated iOS project is a shell. Full Xcode is absent in the current environment. The native Swift reader and physical iOS evidence remain required. If bridge fidelity fails the same locator and selection tests, evaluate SwiftUI/Jetpack Compose shells against identical fixtures before accepting the mobile architecture.

Source references are the Readium Swift/Kotlin feature matrices and Kotlin 3.1.2 navigator APIs. PDFBox Android is Apache-2.0. Readium is BSD-3-Clause. Third-party notices must ship with distribution.
