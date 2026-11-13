# Build 18 automatic walkthrough UAT

Install the signed APK using **Update**. About shows `0.1.0-spike · Build 18` under the existing `com.readle.uat` package.

## Start generation on the Mac

From the Readle checkout, run the local service in a terminal. It uses Python 3.11+, Poppler (`pdfinfo` and `pdftotext`), and the local Ollama models `qwen3.8:27b` and `ministral-3:8b`. For this build use a PDF with selectable text, up to 32 MB and 120 pages.

```sh
python3 scripts/walkthrough_service.py --out output/walkthrough/build-18 \
  --cache output/walkthrough/identity-management
```

Connect your phone to the Mac by USB, enable USB debugging for the UAT device, and accept the Mac in the Android USB debugging prompt. In a second terminal, use Android Studio's Platform Tools. Substitute your phone's serial if several devices are connected.

```sh
~/Library/Android/sdk/platform-tools/adb devices -l
~/Library/Android/sdk/platform-tools/adb -s PHONE_SERIAL reverse tcp:8765 tcp:8765
```

Leave the service running while creating a walkthrough. Restore the port forwarding after reconnecting USB if Android reports that it cannot reach the Mac. The service binds to Mac loopback and calls local inference. Job records and the uploaded source stay under the ignored output directory. The phone saves completed editions and their progress in its local database. You can disconnect the phone once the first lesson opens.

## Checklist

1. Import `2510.25819v1.pdf` or open its existing Library entry. Tap **Build learning edition**. Use **Fill an example learner profile** to try the engineering route, or enter your own background, goal and preference. Tap **Generate on connected Mac**. Check that progress appears and that you can leave the screen, reopen the saved session, and follow the same job.
2. Open **Pass 3 · Assessment** as soon as the first lesson appears. Select an answer, switch to **Pass 2 · Technical**, and return. The same lesson and draft should reappear. Confirm that submission shows the grade and rationale once.
3. In Pass 2, open a source reference. Check the one-based PDF page shown in the lesson against the reader. Use **Back to walkthrough** to return to the same concept and pass. Pass 1 and Pass 2 both end in an ungraded checkpoint.
4. Open the path and visit the lessons about narrowing permissions and de-provisioning. The application questions should ask you to decide an action or predict an outcome using the facts of a new scenario. Try a correct and an incorrect answer.
5. Disconnect USB after the edition is saved. Force-stop the app, enable airplane mode, and reopen the generated session from **Continue**. Verify the same lesson, pass, draft and submitted result. Check that the source reference still opens offline.
6. Start another walkthrough for the same paper with a different goal. Its answers and progress start blank. Reopen the earlier walkthrough to confirm its answers remain available.

Relevant spec sections are §1.3 for aligned navigation, §4.2–4.6 for source-preserving generation, and §5.4 for independent assessment and deterministic grading. This UAT slice covers local text PDF generation, fixed four-lesson routes and single-answer MCQs. OCR, EPUB generation, mastery and recovery, production model selection and budget controls, and physical-device acceptance are pending.

## Android Studio evidence

The Android 16 API 36 phone emulator imported the supplied 33-page PDF through Downloads, submitted the complete engineering profile, and reopened the same generation job from the session list. The Mac reused validated stages from the earlier document run and saved the new edition after checking all eight answer keys. The two replacement questions use source-reviewed scenarios bound to this paper and its cited passages. Other generated lessons use the scenario author and independent reviewer. The final run retried after a local model timeout and reused the completed stages.

The foldable emulator exhibited an Android system-process hang while entering a profile. The full flow moved to the phone emulator. Physical Z Fold7 acceptance remains open.

Pass 3 opened directly from the first lesson. Switching between Pass 2 and Pass 3 preserved the lesson, and a draft answer survived that switch in lesson 3. The label-creation answer graded correct and waiting for an expired token in lesson 4 graded incorrect. After removing port forwarding and force-stopping in airplane mode, **Continue** restored lesson 4, Pass 3, and the submitted result. The result opened PDF page 14 of 33 and returned to the same assessment. Six React Native tests and nine native persistence and migration tests passed.
