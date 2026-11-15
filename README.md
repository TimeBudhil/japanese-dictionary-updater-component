# Updater (japanese-dictionary-updater-component)

Downloads the open Japanese dictionary sources (JMdict, JMnedict, KanjiDic2, KanjiVG, RADKFILE,
Tatoeba) on a schedule and keeps Postgres and Elasticsearch up to date. It is the only writer
of the dictionary data. The read-only API that serves it lives in `../japanese-dictionary-services`.

## Coming back after a while

1. Read **"Next session"** at the top of [`docs/PLAN.md`](docs/PLAN.md).
2. `git status` here and in `../japanese-dictionary-services`. Cross-repo work is tracked in both
   repos' PLAN.md files under shared `X<n>` ids.
3. Start the stack (next section). For how things work, read [`docs/CONTEXT.md`](docs/CONTEXT.md).

Requirements: JDK 26, Docker.

## Run it

```bash
docker compose up --build                          # databases + updater; every feed runs on startup
docker compose up postgres elasticsearch db-init   # databases only
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # updater from the host, against them
```

A profile is required (`local`, `docker` or `prod`); without one, startup fails on purpose.
Profiles, properties, env vars and logins: [`docs/configuration.md`](docs/configuration.md).

| What | Where | Login |
|---|---|---|
| Postgres | `localhost:5432/updater` | owner `updater`/`updater`; read-only `dictionary_reader`/`dictionary_reader` |
| Elasticsearch | `http://localhost:9200` | none |
| Admin API | `localhost:8081` (compose) or `localhost:8080` (`local`) | `Authorization: Bearer dev-token` |

**Admin API**
- `GET /admin/updates` lists the spoke ids: `jmdict`, `jmnedict`, `kanji`, `tatoeba`.
- `POST /admin/updates` runs every spoke in the background (202).
- `POST /admin/updates/{id}` runs one spoke (404 if the id is unknown).

```bash
curl -X POST -H "Authorization: Bearer dev-token" localhost:8081/admin/updates
```

A first full import takes about 16 minutes. Unchanged feeds are skipped by content hash, so
later runs are quick unless upstream changed.

The dictionary service has its own dev stack (ports 5433/9201) and loads it with this updater.
See its README, "Full load".

## Tests

```bash
./mvnw test     # unit tests (parsers, guards); no Docker
./mvnw verify   # plus the *IT integration tests; needs Docker (Testcontainers)
```

## Schema changes

Add a new `src/main/resources/db/migration/V<n>__<description>.sql`. Never edit an applied one:
Flyway checksums every file, comments included. Then, in the dictionary service, run
`sh docker/generate-schema.sh` so its seed schema follows.

## The docs

| File | Holds |
|---|---|
| `docs/PLAN.md` | Next session, open tasks, decisions in force |
| `docs/CONTEXT.md` | How it works: hub and spokes, runs, retirement, data caveats, glossary |
| `docs/configuration.md` | Profiles, properties, env vars, logins |
| `docs/HISTORY.md` | Archive of done work, old decisions and the former ADRs. Read only when digging |
| `CLAUDE.md` | How Claude keeps these docs up to date |

Ids: `U<n>` a decision of this repo, `X<n>` a task shared with the dictionary service. The
dictionary service's own decisions are `D<n>`, in its `docs/PLAN.md`.
