# Security Policy – Standby Light

Standby Light switches off the standby light of Realtek-based Android TVs. That light is only reachable from the
ADB shell, so the app signs in to the TV's own ADB with a key of its own. **Allowing that key gives the app the
same power as a computer you allow to debug the TV.** This page says how that power is kept narrow.

---

## Reporting a Vulnerability

If you discover a security vulnerability:

- Do NOT open a public issue.
- Use GitHub's [Private Vulnerability Reporting](../../security/advisories/new) for this repo.
- Include:
  - A clear description of the issue
  - Steps to reproduce
  - Affected version(s)
  - Any logs or screenshots (sanitized: no IP addresses, network names, device names or accounts)

Please allow reasonable time for investigation and patching before public disclosure.

Responsible disclosure is required.

---

## What the App Can Do With Its ADB Key

- It connects only to `127.0.0.1:5555`, the TV's own ADB. Nothing leaves the TV.
- It sends one fixed command: `service call com.realtek.power.ILedManager/default 1 i32 <state> i32 <0|1>` for
  states 0 to 4. The command is a constant in the app; nothing from outside the app (no intent, file, network or
  setting) can change it or add another.
- It exports no service or provider. Its only entry points are its launcher screen and a receiver for
  `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`, which only schedules that same command.
- The RSA key is made on the TV and kept in the app's private storage (backups are off). It is never shown,
  exported or sent anywhere.

---

## What You Should Know

- **Network debugging (ADB over the network on port 5555) must stay on** for the app to work. Anyone on your
  network who is also allowed to debug the TV could use it; Android asks on the TV before allowing any new key.
  Never forward port 5555 to the internet.
- **Revoking:** Settings → Developer options → *Revoke USB debugging authorizations* removes the app's key (and
  every computer's). Uninstalling the app deletes its key.
- Prefer **Back to normal** and uninstalling over leaving the app on a TV you give away or sell.

---

## Supported Versions

Security updates are applied to:

- The latest tagged release

Older versions may not receive patches.

---

## Secure Development Practices

Standby Light development guidelines:

- The shell command stays a constant; no input of any kind reaches it
- No exported services or providers, no internet beyond 127.0.0.1
- No debug backdoors, no remote logging, no analytics
- Never log or export the key

Pull Requests touching the ADB client, the key or the command will receive additional review.
