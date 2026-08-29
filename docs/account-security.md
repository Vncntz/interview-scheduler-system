# Secure account lifecycle

The account lifecycle applies the same password policy to administrator bootstrap credentials,
new recruiter temporary passwords, authenticated password changes, and signed-link resets:
15–64 Unicode characters and no more than 72 UTF-8 bytes. Passwords are never truncated.

After five consecutive bad-password attempts for an existing active account, the account is locked
for 15 minutes. A successful login clears the failure state and records the login time. Administrators
can unlock recruiter accounts from Recruiter Management. Public login errors intentionally do not
distinguish unknown, inactive, locked, or wrong-password accounts.

Users created with a temporary password must use `/change-password` before entering operational
routes. A successful password change or signed-link reset invalidates known sessions and requires a
fresh login.

## Password reset configuration

Administrator-initiated recruiter resets require all of the following:

- `APP_BASE_URL`: trusted externally visible HTTPS origin, such as `https://iss.example.com`.
  Loopback HTTP is accepted only for local/test use.
- `ACCOUNT_RESET_TOKEN_SECRET`: base64-encoded random data containing at least 32 bytes.
- active email notification settings with complete SMTP host, port, and username.
- `SMTP_PASSWORD` supplied to the application process from the approved secret source.
- an active `PASSWORD_RESET` email template.

Missing configuration disables reset initiation without preventing application startup. Reset links
expire after 30 minutes, rotate any previous outstanding request, and are single use. The database
stores a public random request identifier and a SHA-256 token hash, never the bearer token or reset
link. Rotating the signing secret invalidates all outstanding links.

Notification delivery remains best-effort and occurs after the account transaction commits. There is
no durable outbox or automatic retry guarantee.

## Notification credential handling

The database stores only non-secret notification metadata. The SMTP password is read from
`SMTP_PASSWORD` at runtime and is never displayed or persisted by the notification settings screen.
Missing SMTP credentials do not prevent application startup, but email cannot be enabled and password
reset initiation remains unavailable until the runtime secret and non-secret SMTP settings are both
complete. SMS delivery is not implemented and remains disabled.

Rotate the SMTP credential in the external secret source and restart or redeploy every application
instance that consumes it. Revoke the previous provider credential after the replacement is active
and a controlled delivery check succeeds. Backups created before V5 may still contain the removed
legacy `smtp_password` and `sms_api_key` columns; retain those backups under secret-level access
controls and revoke any historic credentials they contained. Never copy historic secret values back
into the live schema.

## Session limitation

Session expiration uses Spring Security's in-memory `SessionRegistry`. It expires known sessions on
password changes, completed resets, lockout, and recruiter deactivation, and the session is rejected
on its next request. This registry is process-local: in a multi-instance deployment, another node's
sessions are not visible. Distributed deployment requires a shared session store and a coordinated
revocation design before this control can be described as cluster-wide.
