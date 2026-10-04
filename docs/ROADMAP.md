# Roadmap & Research Log

## Phase status

- [x] **Phase 0 — Vertical slice** (this build): mock publisher → MQTT →
      Java backend → Postgres → REST API. Freshness scoring live. Basic
      validation (duplicates, out-of-order, implausible speed, malformed
      messages) live.
- [ ] **Phase 1 — Real CARLA integration**: install CARLA + OpenStreetMap
      road network near REC, replace `mock_bus_publisher.py` with a real
      CARLA Python client reading two-bus telemetry, same JSON schema.
- [ ] **Phase 2 — Reliability + adaptive sync**: reliability engine
      (freshness + consistency + plausibility + historical-consistency
      weighted score), adaptive synchronization priority function, event
      severity model, dashboard.
- [ ] **Phase 3 — Experiments**: fault-injection layer (latency, packet
      loss, duplication, reordering), baseline (fixed-rate) vs proposed
      (adaptive) comparison, metrics collection, plots.

## Design decisions log (Phase 0)

| Decision | Rationale | Status |
|---|---|---|
| `ddl-auto=update` instead of Flyway | Faster iteration while schema is still changing | Will migrate to Flyway before Phase 2 per DB design section of the brief |
| Freshness = `exp(-lambda * AoI_seconds)`, `lambda=0.2` | Matches project brief's candidate formula; `lambda=0.2` chosen so 5s-old data ≈ 37% freshness (1/e) | To be recalibrated once real communication-latency data exists (Phase 3) |
| Mock Python publisher instead of CARLA for Phase 0 | CARLA requires GPU + Unreal Engine components and cannot be verified in a constrained sandbox; isolating the backend pipeline first follows RULE 4 (smallest working vertical slice) | Replaced in Phase 1 |
| Out-of-order detection uses timestamp comparison only (no sequence numbers yet) | Simplest correct check for Phase 0 | Sequence-number-based detection deferred to Phase 2 reliability engine |
| Reliability engine, adaptive sync, event severity NOT implemented yet | Explicitly deferred per phased development philosophy in the brief | Phase 2 |

## Open questions for Phase 1 (CARLA)

- Confirm CARLA version compatibility with Windows + your GPU before install.
- Confirm coordinate system conversion needed between CARLA world coordinates
  and the twin's stored X/Y (brief explicitly flags this as non-trivial).
- Decide whether the CARLA Python adapter runs as a long-lived script or a
  CARLA "actor" tied to the simulation tick.
