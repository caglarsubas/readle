# Android UAT

The product owner is the Android UAT tester. The first delivery is a standalone signed APK supplied directly from this workspace. The Readle UAT app has ID `com.readle.uat`. Preserve the UAT signing identity for updates. Install an incremented build over the prior build to exercise migration and data retention. The build runs without Metro, a laptop connection, a server, an account or an AI key.

## First handoff

Record device/model, Android version, build/version code, install or upgrade, date, result (pass/fail/blocked), steps, expected result, observed result and screenshot where useful. Only share a fixture screenshot when a private book would otherwise appear.

1. Install and launch. Choose Add sample books. Confirm PDF and EPUB appear.
2. Select a book and inspect Sessions. Merely inspecting the book creates no session.
3. Start reading. Navigate to the equation or a later passage. Long press, adjust selection, highlight and save a note.
4. In PDF use A+/A− zoom controls. For a two-page selection, use Selection → Extend to next page, then select the ending passage. In EPUB change font size and verify the highlighted words.
5. Return to Library, select the same book and choose Start new session. Move to a different page/chapter. Check the shared note appears in Notes.
6. Force-stop using Android settings. Enable airplane mode. Reopen, select the book and resume each saved session. Verify independent passage/zoom/font state and the original saved note.
7. Select the other book and verify its session list is independent. Global Continue should offer the same saved sessions.
8. Import several files using the Android picker. Include the fixture twice and a corrupt file. Successful books remain readable. Retry only the failed item.
9. Install the next version over this build. Verify both sessions and annotations survive.

Pending areas include physical-device acceptance, pinch zoom, continuous PDF cross-page dragging, EPUB search, PDF TOC extraction, shelves, accessibility text alternatives, performance measurements and iOS integration. AI/chat, course conversion and quizzes belong to subsequent milestones and are pending.

## Result template

- Build and APK checksum
- Device and Android version
- Scenario and steps
- Expected behavior
- Observed behavior
- Result and severity
- Screenshot or safe diagnostic details

Engineering checks, emulator checks and this user acceptance result are independently recorded. The full Phase A gate requires physical iOS and Android evidence.
