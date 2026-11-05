# Reader dependencies

The application uses React and React Native under the MIT license, Readium Kotlin under BSD-3-Clause, PDFBox Android under Apache-2.0, and AndroidX under Apache-2.0. Dependency packages contain the corresponding license and notice files. Preserve them and complete the transitive-dependency license inventory before store distribution.

- React Native source and license — https://github.com/facebook/react-native
- Readium source and license — https://github.com/readium/kotlin-toolkit
- PDFBox Android source and license — https://github.com/TomRoush/PdfBox-Android
- AndroidX source and licensing — https://android.googlesource.com/platform/frameworks/support/

The sample publications are self-authored CC0 fixtures. This notice does not grant rights to a user's imported books.

The navigator in build 13 is compiled from Readium Kotlin 3.1.2 commit `71074ea0c424eabbc39a93b48a4cefe366f7741b` with the reviewed `patches/readium-selection.patch` and Readle's ActionMode delegate. The verified archive's BSD license is packaged in APK assets as `readle_readium_LICENSE.txt`. The upstream navigator's bundled PhotoView jar and existing dependency notices remain included. Build setup is documented in `apps/mobile/android/readium-navigator-patched/README.md`.
