# Build 17 walkthrough test

Install `readle-uat-17.apk` with **Update**. About shows `0.1.0-spike · Build 17`. The package uses `com.readle.uat`, the existing UAT signer and SQLite schema 2.

1. Import the supplied *Identity Management for Agentic AI* PDF (`2510.25819v1.pdf`). Open the paper and tap **Plan a walkthrough**. The PDF is supplied separately through the Android file picker.
2. Select **Design permissions for sub-agents** and **I know the basics**. Start the path. The first lesson uses the recursive delegation example from section 4.4. Tap that source reference and check that the viewer opens printed page 26 (PDF page 27 of 33). Tap **Back to walkthrough**.
3. Advance to **Check how permissions narrow**. Enable the helper's write access and observe that the write is allowed. Disable writes at the resource and observe that the same request is denied. Switch to **Read** to see the resource policy allow the request.
4. Force-stop Readle, reopen in airplane mode and use **Continue**. The same lesson and simulator inputs should reappear. Fold and unfold while the lesson is open and check the controls on both screens.
5. Start a second walkthrough for **Review an agent for security risks**. The first lesson covers a task that outlives the user's login. Reopen the earlier delegation session and check that its position and simulator settings are independent.

The sample is a curated set of seven lessons drawn from sections across this paper. Its route depends on the goal and experience choices. It has no live generation for other documents. The file identity is SHA-256 `b019c9420565afe6a3f81fcd8e9b3375f546003cecab93ecfa25d965b2f68bb2`.

On 2026-11-13 the signed APK upgraded the Build 16 library on a Pixel Fold API 36 emulator. Android Studio review covered import, two goal-specific paths, the printed page 26 reference and return, all three write outcomes, restoration after force-stop in airplane mode, and the unfolded lesson. Six React Native tests and eleven native persistence, migration and PDF regression tests passed. Physical Z Fold7 acceptance is open.
