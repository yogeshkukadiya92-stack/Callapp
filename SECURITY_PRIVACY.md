# Security and privacy

- Passwords are never persisted. Access and refresh tokens are encrypted at rest with an Android Keystore AES-GCM key before being written to DataStore. Production refresh-token rotation and revocation responses remain server-contract requirements.
- No secrets, production hosts, tokens, or signing material belong in source control.
- TLS and platform network security defaults are enforced; cleartext traffic is disabled.
- The generated installation ID is used for device binding instead of raw hardware identifiers.
- Backend authorization is authoritative; hidden controls are not security boundaries.
- Phone masking, business-call-only retention, audit metadata, revocation, and server-configured retention are required before production rollout.
- Analytics must never include phone numbers, note contents, tokens, or other customer payloads.
- Call recording is absent from V1.

Authentication UI observes a durable `SessionState`; invalid/decryption-failed state resolves to signed out rather than exposing or crashing on token material. Logout removes the encrypted payload. Debug fake-auth tokens use the same encrypted store and repository boundary as production authentication.

Device binding uses a random app-install UUID stored in private DataStore, not IMEI, Android ID, serial number, or another raw hardware identifier. Registration sends the generated ID plus manufacturer/model, Android version, app version, and a user-facing device name. Unknown backend device states fail closed as `PENDING_APPROVAL`; only `ACTIVE` sessions may enter CRM screens.

Authenticated requests receive the bearer access token through an OkHttp interceptor. A synchronized authenticator rotates access and refresh tokens once after HTTP 401 and retries the original request. Concurrent failures reuse the newly rotated token. Connectivity/server failures preserve the local session; an explicit 400/401/403 refresh rejection or `X-Session-Revoked: true` response clears encrypted credentials and returns the employee to Login.
# Permission and onboarding policy

First-run onboarding explains local business-data storage and the optional default-dialer capability before sign-in. Onboarding itself never opens a system permission prompt. Phone role, call-notification, and call-placement access are requested only from their related feature and are represented centrally as granted, denied, permanently denied, not required, or role missing. Denial never blocks manual CRM use.

# Session expiry and revocation

Authenticated requests attach the current encrypted access token unless the caller supplied an explicit authorization header. A single-flight authenticator refreshes and rotates both tokens after a 401, then replays the request once. A rejected refresh (400/401/403) or a 403 response carrying `X-Session-Revoked: true` clears the encrypted session and returns the app to signed-out state. Retry depth is bounded to prevent authentication loops.
