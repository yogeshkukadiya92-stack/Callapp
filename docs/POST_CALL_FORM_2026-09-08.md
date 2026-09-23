# Post-call form

The automatic post-call popup now contains the requested 20 status choices, an optional next follow-up date/time, a note box, Save and Cancel. Both tracked call return paths open this form.

For assigned leads, saving uses the existing transactional call-result, note, follow-up notification and dashboard outbox flow. Status IDs and codes retain existing mappings where available. For unassigned numbers, status and date are saved as call-note text; no reminder is scheduled until a lead is assigned. The popup explains this limitation.

The APK is a debug build. Live dashboard acceptance of new status codes and physical-device call completion have not been verified.
