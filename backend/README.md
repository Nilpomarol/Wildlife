# Wildlife sync backend (prototype)

This local service is the single observation-sync adapter. It caches public iNaturalist observations by immutable user ID and observation UUID, uses an overlapping `updated_since` cursor, performs a full reconciliation every 24 hours, and records XP idempotently only after Wildlife confirms a match.

Run from the repository root:

```powershell
python backend/server.py
```

For a USB-connected Android device, expose the local port once:

```powershell
adb reverse tcp:8765 tcp:8765
```

The prototype listens only on `127.0.0.1:8765`. Production still needs Wildlife session authentication, HTTPS, per-user authorization, retention/deletion jobs, and managed storage.
