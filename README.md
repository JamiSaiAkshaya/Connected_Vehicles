# Connected Vehicles — Digital Twin (Phase 0: Vertical Slice)

**Status:** Phase 0 only. This proves the pipeline
`Telemetry Publisher → MQTT → Java Backend → PostgreSQL → REST API` works end-to-end
on your machine. CARLA is **not** integrated yet — a Python script
(`carla/publisher/mock_bus_publisher.py`) stands in for it, publishing the exact
JSON schema CARLA will eventually produce. Once this phase runs cleanly, Phase 1
swaps the mock for a real CARLA scenario without touching the backend.

No Docker is used, per your instruction — everything below installs and runs
natively on Windows.

---

## 1. Prerequisites — install these first

Run each check in **PowerShell** or **Command Prompt** to see what you already have.

| Tool | Check command | If missing |
|---|---|---|
| Java 21 | `java -version` | Install **Eclipse Temurin 21 (LTS)**: https://adoptium.net/temurin/releases/?version=21 — download the `.msi`, run it, tick "Set JAVA_HOME" during install. |
| Maven | `mvn -version` | Download the binary zip from https://maven.apache.org/download.cgi, extract to e.g. `C:\Program Files\Apache\maven`, add `<maven>\bin` to your `Path` environment variable. |
| PostgreSQL | `psql --version` | Download installer from https://www.postgresql.org/download/windows/ — during install, remember the password you set for the `postgres` user, and keep default port `5432`. |
| Mosquitto (MQTT broker) | `mosquitto -h` | Download installer from https://mosquitto.org/download/ (Windows binaries). During install, note the install path (usually `C:\Program Files\mosquitto`). |
| Python 3.10+ | `python --version` | Download from https://www.python.org/downloads/windows/ — tick "Add python.exe to PATH" during install. |

After installing anything, **close and reopen your terminal** so PATH changes take effect.

---

## 2. One-time setup

### 2.1 Create the database

Open a terminal and run:

```powershell
psql -U postgres -f database\create_database.sql
```

It will prompt for the `postgres` user password you set during install.

### 2.2 Install the Python publisher's dependency

```powershell
cd carla\publisher
pip install -r requirements.txt
cd ..\..
```

### 2.3 Configure credentials (no hard-coded secrets)

The backend reads config from environment variables, falling back to safe
defaults in `backend/src/main/resources/application.yml`. If your Postgres
password isn't `postgres`, set it before running the backend:

```powershell
$env:POSTGRES_PASSWORD = "your_actual_password"
```

(This only lasts for the current terminal session — re-run it each new session,
or set it permanently via System Properties → Environment Variables.)

---

## 3. Running the system (3 terminals)

Open **three separate terminals**. Order matters: broker → publisher → backend
(or backend → publisher — the backend will reconnect automatically if the broker
starts late, but the broker must exist before either the publisher or backend
tries to connect).

### Terminal 1 — MQTT broker

```powershell
cd database
mosquitto -c mosquitto.conf -v
```

Leave this running. You should see `Opening ipv4 listen socket on port 1883.`

### Terminal 2 — Backend (Spring Boot)

```powershell
cd backend
mvn spring-boot:run
```

Wait for `Started DigitalTwinApplication in ... seconds`. If it fails, see
Troubleshooting below.

### Terminal 3 — Mock telemetry publisher

```powershell
cd carla\publisher
python mock_bus_publisher.py
```

You should see JSON telemetry lines printing once per second for `BUS-01` and `BUS-02`.

---

## 4. Verify it worked

With all three terminals running, open a browser (or run `curl` in a 4th terminal):

```
http://localhost:8080/api/twins/buses
```

Expected: a JSON array with two objects (`BUS-01`, `BUS-02`) showing live
`lastValidPositionX/Y`, `freshnessScore` (close to 1.0), and `syncStatus: "SYNCED"`.

Also try:

```
http://localhost:8080/api/twins/bus/BUS-01
http://localhost:8080/api/health
```

**Freshness check:** stop Terminal 3 (Ctrl+C), wait ~15 seconds, then refetch
`/api/twins/bus/BUS-01`. `freshnessScore` should have dropped and `syncStatus`
should flip to `"STALE"` (threshold is 10s by default) — this confirms the
freshness/staleness logic is actually live, not just a static field.

---

## 5. Troubleshooting

**Backend fails with `Connection refused` to Postgres**
Postgres service isn't running. Open Windows Services (`services.msc`), find
`postgresql-x64-...`, ensure it's "Running". Or check the port/password match
`application.yml` / your `POSTGRES_PASSWORD` env var.

**Backend fails with `MqttException ... Connection refused`**
Mosquitto (Terminal 1) isn't running yet, or is on a different port. Confirm
Terminal 1 shows `Opening ipv4 listen socket on port 1883.`

**`mvn` or `mosquitto` not recognized**
PATH wasn't updated, or terminal wasn't reopened after install. Re-check
section 1 and open a fresh terminal.

**`/api/twins/buses` returns `[]` (empty)**
The backend hasn't received any messages yet. Confirm Terminal 3 is actively
printing telemetry, and that both are pointed at `localhost:1883`.

**Port 8080 already in use**
Set `$env:SERVER_PORT = "8081"` before `mvn spring-boot:run`, then use that
port in the URLs above.

**Maven downloads are slow/fail on first run**
Normal — Maven fetches dependencies from the internet the first time only
and caches them in `~/.m2`. Just let it finish; subsequent runs are fast.

---

## 6. What's intentionally NOT in Phase 0

- Real CARLA simulation (Phase 1)
- Reliability engine, adaptive synchronization, event severity prioritization (Phase 2)
- Dashboard UI (Phase 2+)
- Fault-injection / network-degradation experiments (Phase 3)
- Flyway migrations (currently `ddl-auto=update` for iteration speed — documented
  design decision, see `application.yml` comments)

See `docs/` for the phased roadmap and the research-contribution framing from
the original project brief.

---

## 7. Project structure

```
Connected_Vehicles/
├── backend/                # Spring Boot Java backend
│   └── src/main/java/com/project/digitaltwin/
│       ├── vehicle/         # Vehicle, VehicleState entities
│       ├── twin/            # DigitalTwinState + DigitalTwinService (core logic)
│       ├── communication/   # TelemetryMessage DTO, validator
│       ├── freshness/       # FreshnessCalculator (AoI-based)
│       ├── config/          # MQTT wiring
│       ├── api/             # REST controllers
│       └── repository/      # Spring Data JPA repositories
├── carla/publisher/         # Mock telemetry publisher (Phase 0 stand-in for CARLA)
├── database/                # Mosquitto config, DB creation script
├── docs/                    # Roadmap, research notes
└── experiments/             # Reserved for Phase 3 experiment configs/results
```
