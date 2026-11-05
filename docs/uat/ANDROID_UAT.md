# Install build 11 and run Android UAT

The product owner is the Android UAT tester. Build 11 is a standalone, privately supplied APK, about 60 MiB, for Android 7.0/API 24 or newer. Its application ID is `com.readle.uat`, and it appears as Readle in the app list. It runs independently after installation. The current build covers the original reader and saved sessions. AI, courses and quizzes are later milestones.

The APK is in `output/android/readle-uat-11.apk` on the build Mac. This is a local file path. The public Git repository contains the source and instructions. The current APK is supplied separately from this workspace. Its SHA-256 is `b55d88d17ff8f85d740b8a8c7f07d30c931d1e4e0f9c78761965e0e8ca01ad35`.

## Install from the development Mac by USB

The Android SDK and ADB are already installed on this Mac.

1. On the phone, open Settings and find Build number, usually under About phone or Software information. Tap Build number seven times and enter your unlock code if requested.
2. Open Developer options in Settings and enable USB debugging.
3. Connect the unlocked phone to the Mac with a data-capable USB cable. Accept the phone's Allow USB debugging prompt for this computer.
4. In Terminal on this Mac, run the following command. It checks the APK checksum, targets one USB-connected phone, installs the app, copies four UAT import files into Downloads/Readle-UAT, and opens Readle.

```sh
/Users/caglarsubasi/Desktop/prometa/pocs/readle/scripts/install-android-uat.sh 11
```

If the script reports that it cannot see the phone, keep it unlocked, check the authorization prompt and USB cable, and run it again. If more than one physical device is attached, disconnect the others. For a different SDK location, set `READLE_ADB` to the full path of its `platform-tools/adb` executable.

The installer uses `adb install -r`, retaining existing app data during an update. After installation you can disconnect the cable and begin offline testing. Android's official setup and ADB instructions explain USB authorization and device detection. [Physical device setup](https://developer.android.com/studio/run/device#setting-up), [ADB instructions](https://developer.android.com/tools/adb#Enabling)

## Install by opening the APK on the phone

1. Transfer the private `readle-uat-11.apk` file from the Mac to the phone using a private file-transfer method you already use. A separate `readle-uat-11-fixtures.zip` contains the four import-test files.
2. Open Files/My Files on the phone and tap the APK.
3. If prompted, open Settings for that file-opening app and enable Allow from this source under Install unknown apps. Return to the APK and choose Install, then Open. Menu names vary by manufacturer. [Google's installation instructions](https://support.google.com/pixelphone/answer/7391672?hl=en)
4. For batch-import testing, extract the fixture ZIP into Downloads/Readle-UAT.

## Run the checklist

Begin by recording the phone model, Android version, build 11, date and whether this is a first install or update. Use the self-authored samples first.

| ID | Action | Expected result |
|---|---|---|
| UAT-01 | Launch Readle and choose Add sample books. | Both PDF and EPUB entries appear and can be selected. |
| UAT-02 | Select the PDF book. Record the session count, return to Library, then select it again. | Inspecting the book preserves the session count. A fresh book has zero sessions. |
| UAT-03 | Choose Start reading, or Start new session if sessions already exist. Record its label. Navigate to PDF page 2, which contains the equation. | The reader shows the original page and reports Page 2 of 3. |
| UAT-04 | Long-press a word until selection appears, adjust its endpoints, then choose Selection → Add note. Tap the Your note field, type `UAT note A`, and Save. | Notes lists the selected quote and the note you typed. |
| UAT-05 | Change PDF zoom using Zoom +/Zoom − or pinch. Return to Library and choose Start new session. Record this second label and navigate to page 3. Open Notes, verify the first note, then close the dialog. | The two sessions have independent positions. The note is shared within the book. |
| UAT-06 | Open Android Settings → Apps → Readle → Force stop. Enable airplane mode. Reopen Readle, select the PDF and open each recorded session. | The first session returns to page 2 with its zoom/selection state. The second returns to page 3. The saved note remains available. |
| UAT-07 | Return to Library and select the EPUB entry. Inspect its sessions, start one, open Contents and choose chapter 2. Change font size and add a highlight using the native selection menu. Force-stop and reopen that session. | Its list is independent of the PDF. Chapter, reading position, font preference and highlight restore correctly after reflow/restart. |
| UAT-08 | From the Library screen choose Continue and open an existing session. | The same saved session opens at its saved passage. |
| UAT-09 | In the PDF, select the start of a passage, choose Selection → Extend to next page, select its ending passage and save a highlight. | The annotation retains both original page locations and can be tapped on either page or opened from Notes → highlight → Open source. |
| UAT-10 | Disable airplane mode. Choose Import books and multi-select the four files in Downloads/Readle-UAT. | The two valid sample formats are readable, the exact PDF copy is recognized as a duplicate, and the intentionally corrupt PDF fails independently. |
| UAT-11 | Retry only the corrupt import. | Its failure remains isolated and the valid books, sessions and annotations remain available. |
| UAT-12 | Install build 11 over the existing build 10 and reopen both sessions. | The update preserves books, session positions and annotations. Existing books, highlights, notes and both session positions must survive. |

Use Android's Force stop button for restart testing. Uninstalling Readle or clearing its storage removes the local library and sessions. Install updates over the existing app using the same signing identity.

## Build 11 feedback checks

Install this APK over your existing app by opening it in My Files, as you did for build 10. Choose Update when Android asks. Keep the installed app and its storage so the upgrade can retain your library.

| ID | Action | Expected result |
|---|---|---|
| FB-01 | In both PDF and EPUB, tap a saved gold highlight, including one made in build 10. | A Highlight menu shows that quote and its attached note. It offers Add/Edit note, Copy quote, Share quote, Open source and Delete highlight. |
| FB-02 | Edit an attached note, close the reader, force-stop and reopen it in airplane mode. Open a second session for the same book and tap that highlight. | The same single highlight has the edited note in both sessions. Its original words and both session positions are preserved. |
| FB-03 | Copy a quote and paste it into a temporary note. Try Share quote and cancel the Android share sheet. Choose Delete highlight → Keep, then Delete highlight → Delete. | Copy contains the quote. Share contains the quote and book title. Keep preserves the object. Delete removes its highlight and attached note from both sessions. |
| FB-04 | Pinch and double-tap the PDF, scroll both directions, then tap a highlight. Rotate and switch between the Z Fold7 cover and inner screens. Force-stop and reopen the session. | Zoom stays within limits, controls remain reachable, the highlight menu opens at the correct words, and page, zoom and horizontal/vertical position restore. |
| FB-05 | In EPUB, change text size, rotate or unfold, then tap the same highlight. In PDF, tap an overlapping highlight and each half of a two-page highlight. | EPUB retains the original highlighted words. PDF offers a chooser for overlapping objects. Either page opens the same two-page object. |

These checks are pending physical-device feedback for build 11. Build 10's accepted results are recorded separately in [the UAT results](UAT_RESULTS.md).

## Send results

Reply in this chat with the following details. Report blocked steps with the exact on-screen message. For failures, add reproduction steps and a screenshot of a sample book if possible.

```text
Build: 11
Phone model:
Android version:
Install method: USB / APK opened in Files
First install or update:
UAT-01: Pass / Fail / Blocked
UAT-02: Pass / Fail / Blocked
UAT-03: Pass / Fail / Blocked
UAT-04: Pass / Fail / Blocked
UAT-05: Pass / Fail / Blocked
UAT-06: Pass / Fail / Blocked
UAT-07: Pass / Fail / Blocked
UAT-08: Pass / Fail / Blocked
UAT-09: Pass / Fail / Blocked
UAT-10: Pass / Fail / Blocked
UAT-11: Pass / Fail / Blocked
UAT-12: Pass / Fail / Blocked
FB-01..05: Pass / Fail / Blocked
Failure details:
```

The current supported inputs are text PDF and static UTF-8 EPUB, subject to the limits in READER_SPIKE_STATUS.md. Continuous cross-page dragging, EPUB search, PDF TOC extraction, shelves and full accessibility acceptance remain pending. Engineering, emulator and physical-device UAT results are recorded independently. The complete Phase A gate also requires native iOS integration and physical iOS evidence.
