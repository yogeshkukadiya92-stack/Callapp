# Call notes demo — 8 September 2026

Changes: automatic post-call navigation now opens the notes dialog for both matched and unmatched calls. Call history has an explicit ADD NOTE action; call details has ADD CALL NOTE and preserves access to result/follow-up entry for matched leads. The dialog preserves its draft when deferred and disables editing/dismissal while saving. Newly ended calls take priority over older pending notes, with duplicate callbacks preserving the active target.

Verification: live-default APK build and all 73 unit tests passed (zero failures/skips). An isolated com.callflow.qa build was installed in Resizable_Experimental. A simulated incoming call matched Anita Sharma, was answered and ended using app controls, and automatically opened Call notes with 18s, matching Android CallLog. A sample note was saved and displayed in Notes & tags. Both the call-details ADD CALL NOTE button and history ADD NOTE button reopened the dialog. The emulator was left with the manual popup open. Crash buffer was empty.

The synthetic system CallLog row 4 was removed after testing; the QA app retains the saved demo call and note. The live app was force-stopped throughout the test. No real carrier call or live dashboard delivery was tested.

Artifacts:
- outputs/CallFlow-call-notes-fixed.apk: normal application ID, live-default configuration, debug signed.
- outputs/CallFlow-call-notes-demo.apk: isolated com.callflow.qa, fake backend, unreachable local endpoint.
- outputs/notes-demo/automatic-popup.png
- outputs/notes-demo/saved-note.png
- outputs/notes-demo/history-add-note.png
- outputs/notes-demo/manual-popup.png
