# Android call integration

`CallIntegrationManager` separates Android Telecom from CRM behavior. A call attempt is committed before the current implementation opens the system dialer. `CallFlowInCallService` records lifecycle timestamps and reuses the recent app-created attempt to prevent duplicate outgoing records.

Automatic tracking will add a role-onboarding coordinator (`RoleManager.ROLE_DIALER`), a narrowly scoped `InCallService`, timestamped lifecycle events, and lead matching through canonical phone numbers. Permissions are requested individually only after explaining their enabled feature. Denial results in manual mode.

The default-Phone path now includes a lock-screen-capable `InCallActivity`, CallStyle incoming/ongoing notifications, answer, reject, disconnect, mute, speaker and hold controls, and an external `ACTION_DIAL` entry screen. When the Phone role is held, outgoing calls use `TelecomManager.placeCall`; manual mode continues to use the system `ACTION_DIAL` flow.

Production enablement still requires physical-device validation across supported Android/OEM versions, dual-SIM handling, Bluetooth audio routing, emergency-call behavior, and notification/full-screen policy review. The app will not inspect another dialer UI, use Accessibility, rely on undocumented OEM APIs, or record audio.
# Safe call matching

Telecom numbers are normalized before lookup. A call is linked to a lead only when exactly one lead matches the normalized number. An outgoing Telecom call reuses a locally initiated attempt only when exactly one open outgoing attempt exists within the two-minute correlation window. Zero or multiple candidates are never guessed: the app creates an unlinked, durable call record that can be reconciled later.
