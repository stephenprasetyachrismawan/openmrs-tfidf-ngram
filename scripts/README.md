# Local Environment Commands

Run these commands from `openmrs-distro-referenceapplication`.

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml build backend
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d
docker compose -f docker-compose.yml -f docker-compose.local.yml ps
docker compose -f docker-compose.yml -f docker-compose.local.yml logs --tail=100 backend gateway db
docker compose -f docker-compose.yml -f docker-compose.local.yml down
```

The local gateway is available at `http://localhost:8081` because port 80 is
already occupied by Apache in WSL. Run the smoke test from the workspace root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\verify-openmrs.ps1
```

The database and OpenMRS data volumes are intentionally preserved by `down`.
Do not use `down -v` unless a complete local data reset is explicitly needed.
