# Updater — History

Archive. Not read by default (see `CLAUDE.md`). Newest entry first; entries are never edited.
Every decision id (`U<n>`, `X<n>`) that left `docs/PLAN.md` keeps its full text here.

## 2026-11-14 — Docs restructure (U10)

Archived word for word at commit `6ec308d`: the old `NEXT_STEPS.md` (done sections 2, 3, 6 and
the decisions U1–U9), both ADRs (their rules now live in `docs/CONTEXT.md`, their decisions in
`docs/PLAN.md` as U11 and U12), and the cross-repo task text from the dictionary service.

### NEXT_STEPS.md as of 2026-11-14


All four spokes (JMdict, JMnedict, Kanji, Tatoeba) are implemented, covered by unit and
Testcontainers integration tests, and verified end-to-end on real upstream data (2026-09-27).
Its history is in this repo's git log.

#### 1. Go live on AWS Cognito

The code is ready (`hub/CognitoJwtDecoderConfig`, `hub/SecurityConfig`); it needs a real user pool.

- [ ] Create (or pick) the user pool, an app client for admin callers, and a Cognito group
      `updater-admin` (or change `updater.admin.required-group`). Add admin users to the group.
- [ ] Configure the deployed environment: the `prod` profile already sets
      `updater.admin.stub-auth=false` and reads `COGNITO_ISSUER_URI`
      (`https://cognito-idp.<region>.amazonaws.com/<userPoolId>`) and `COGNITO_CLIENT_IDS`.
- [ ] Smoke-test with a real **access** token (ID tokens are rejected by design): expect 200 on
      `GET /admin/updates` for a group member, 403 for a non-member, 401 without a token.
- [ ] Once no environment uses the dev token, delete `hub/StubJwtDecoderConfig` and the
      `updater.admin.dev-token` / `stub-auth` properties.

#### 2. Schema migrations: done (2026-10-06)

Flyway owns the schema (`src/main/resources/db/migration`); Hibernate runs with
`ddl-auto=validate`, so an entity/schema mismatch fails startup. `V1__baseline.sql` is the
Hibernate-generated schema as of 2026-10-06 (verified identical via `pg_dump` diff).

- Schema changes: add a new `V<n>__<description>.sql`; never edit an applied one.
- Pre-Flyway databases with that exact schema are adopted as V1 by
  `spring.flyway.baseline-on-migrate=true` (the compose DB was, keeping its 2026-09-27 data).
  A DB whose schema differs from V1 (anything created before 2026-09-27) must be reset:
  `docker compose down -v` (from the repo root; this deletes the data volumes).

#### 3. Entries removed upstream: done (2026-10-06)

An entry missing from a changed upstream file is deleted from Elasticsearch and **retired** in
Postgres (`retired_at` set, row kept). It is un-retired and re-indexed if it comes back. Kanji
sub-sources clear only their own data, and a kanji is retired once no sub-source has it. The
guard `updater.<feedId>.retire-guard` defaults to 0.98. Policy and reasons:
`docs/adr/0001-retire-in-postgres-delete-from-search.md`.

- [ ] The future API backend must filter `retired_at IS NULL` (a view or a default repository
      filter there).
- [ ] Tune each feed's `retire-guard` from the logged `Retirement [<feed>]` counts once a few
      real releases have run. First real run (2026-10-06, against the 2026-09-27 load):

      | Feed | Active before | Retired | Notes |
      |---|---|---|---|
      | jmdict | 218,868 | 1 (`1905600` 深い霧) | ~37 new entries added; ES `vocab` = 218,867 = active rows |
      | jmnedict | 743,675 | 1 (`5540981` 天文科学館) | |
      | kanji.kanjidic2 | 13,108 | 0 | |
      | kanji.radkfile, kanji.kanjivg, tatoeba | | | unchanged upstream (hash-skip) |

      About 0.0005% per feed for 9 days of churn, far below the 2% the default guard allows.
- [ ] Not done: radicals that RADKFILE drops stay in `radical` (only kanji links are cleared).
      Purging long-retired, unreferenced rows is a possible later cleanup job.

#### 4. Run time of changed-file runs

V3 indexes the foreign-key columns. Without the indexes, updating existing JMdict entries
full-scanned the child tables for every entry (~25 entries/s, ~2.5 h). With them, a changed
JMdict file takes ~10 min and JMnedict ~4 min (2026-10-06). The rest of the time is
statement round trips: Hibernate deletes and inserts child rows one at a time, and `IDENTITY`
ids block insert batching. If run time matters, switch to `SEQUENCE` ids / bulk upserts.

#### 5. Jreibun: not ingested

Out of scope: it isn't openly licensed or publicly downloadable. See
`docs/adr/0002-open-data-sources-only-no-jreibun.md`.

#### 6. Profiles and local Docker stack: done (2026-10-23)

Spring profiles `local` (IDE), `docker` (compose service) and `prod` (AWS). There is no
default profile, so a start without one fails fast. `docker-compose.yml` runs
Postgres, Elasticsearch, `db-init` (creates the read-only `dictionary_reader` login) and the
updater, which runs every feed on start-up. Commands: root `README.md`. Profiles,
properties, env vars and logins: `docs/configuration.md`.

Verified 2026-10-23: `docker compose up --build` reused the existing data (218,867 active
vocab entries), `dictionary_reader` can read and gets "permission denied" on writes, the health
endpoint is UP, the admin API returns 401 without a token, and the image without a
profile fails on the missing datasource URL.

#### 7. AWS deployment notes (not started)

The `prod` profile is ready; the infrastructure is not. Decisions made so far:
RDS Postgres 16 and **Elastic Cloud** (ES 9.x; AWS OpenSearch doesn't work with the 9.x
client), secrets as plain env vars (`docs/configuration.md`), Cognito for the admin API.

- [ ] **Image**: build `Dockerfile` and push it to ECR. The build skips
      tests, so CI must run `./mvnw verify` (Testcontainers needs Docker) before it.
- [ ] **Runtime**: e.g. ECS Fargate. Run **exactly one** task: the cron is in-process,
      so two tasks would update every feed twice at once. More tasks would need a lock
      (e.g. ShedLock) or an EventBridge-scheduled task instead of `@Scheduled`.
- [ ] **Secrets**: inject `DB_*`, `ELASTICSEARCH_*` and `COGNITO_*` from Secrets Manager
      or SSM through the task definition's `secrets`. The app only reads env vars.
- [ ] **Cron time zone**: the container runs in UTC, so `0 0 3 * * *` means 03:00 UTC
      (locally too). Set `TZ` or change the cron if another time matters.
- [ ] **Memory**: the JVM uses 75% of the container memory (`-XX:MaxRAMPercentage=75`).
      Size the task from a measured JMdict run.
- [ ] **Network**: outbound HTTPS to the upstreams (EDRDG, GitHub releases for KanjiVG,
      Tatoeba) through a NAT gateway; inbound only from admin callers (internal
      ALB/VPN). Health check: `GET /actuator/health`.
- [ ] **RDS**: the datasource user owns the schema (Flyway runs as it on start-up).
      `db-init` is local only. Create the API backend's read-only role in RDS through
      infra (same grants as `docker/postgres/dictionary_reader.sql`, with a real password).
- [ ] **Elastic Cloud**: one API key for the updater (write `vocab`, `example_sentence`)
      and a separate read-only key for the API backend.
- [ ] Observability goes to its **own** Elasticsearch/Kibana, never the search cluster
      (see 8).

#### 8. Open items from the 2026-10-23 session

- [ ] **ELK monitoring, tracing and logging**: a separate design session. Decided: a
      separate Elasticsearch + Kibana for observability, never the search ES. Open:
      tracing (OpenTelemetry vs Elastic APM agent), log shipping (Filebeat, Logstash or
      an OTel collector), log format (structured JSON), and which metrics/dashboards.
      Actuator is in place (only `health` exposed).
- [ ] **Config server**: may replace the duplicated `local`/`docker` property files.
- [ ] **Container diagram** (`written_reports/Architectures/Container_Diagram_V03.JPG`):
      says MySQL; the relational DB is Postgres. Add the local Docker stack and the
      separate observability ES.
- [ ] **Overlapping runs**: admin-API and start-up runs share one queue, but the cron
      run uses the scheduler thread and can overlap a manual run of the same feed.
- [x] **Volume names**: _done 2026-11-14._ The compose file moved into this repo (U2) with
      default volume names; the old `updatercomponent_*` volumes were deleted.
- [ ] **Local Elasticsearch has no auth**: the API backend can write to it locally. A
      read-only ES user needs `xpack.security.enabled=true` in compose.
- [ ] **Cleanup**: remove `hibernate.dialect` (Hibernate warns HHH90000025: it's
      detected automatically); Spring Boot 4.1.2 is out (on 4.1.1).

#### 9. Decisions (2026-11-14, history restored + Docker run)

Made while testing this repo with the dictionary service (`~/repos/japanese-dictionary-services`).
The same decisions are in that repo's `docs/PLAN.md` (numbers in brackets). Rule [D70]: a
decision that changes this repo's code, schema, feeds or compose file is logged in both repos.

| # | Decision | Reason / impact |
|---|---|---|
| U1 | **This repo is the updater's home, with its full history** [O10 → D73]. It was created from an older commit (`b6fdd02`, 2026-09-26) of the earlier combined repository, so five later commits (integration tests, Flyway, retirement, FK indexes, profiles) were missing. They were restored with `git format-patch` + `git am`, keeping author and dates | The split was meant to move the newest code. The combined repository is retired; nothing should point to it |
| U2 | `docker-compose.yml` **moved here** from the combined repository's root, with `docker/postgres/dictionary_reader.sql`: build context `.`, `updater-*` container names, default volume names, ES **9.4.8** [D57] | A split repo needs its own stack. 9.4.8 is what the dictionary service runs; the client here is 9.4.5, which works with it. Testcontainers still use 9.4.5 |
| U3 | **Sense lists keep the JMdict order**: `@OrderColumn` `gloss_order` / `pos_order` / `misc_order` (0-based, PK with `sense_id`), Flyway **V4** [X8] | The first gloss is the most important; the dictionary service reads these exact names |
| U4 | V4 on an existing DB **deletes the unordered sense lists and forgets the `jmdict` feed version**, so the next run re-imports every entry [D61] | No order can be rebuilt from old rows. "Drop and re-import" (user's call), done by the migration instead of by hand. Until that run, senses have no glosses or tags |
| U5 | V4 **drops V3's `sense_*_sense_id_idx`** | The new PKs start with `sense_id`, so they index the foreign key; `everyForeignKeyColumnIsIndexed` accepts them |
| U6 | `KanjiFileFetcher` errors carry the **status, rate-limit headers and the first 300 body characters** [D69] | A one-off GitHub 403 could not be explained. The GitHub API call itself is already gone (KanjiVG's latest release comes from the `releases/latest` redirect) |
| U7 | Open [X10]: POS/misc are stored as the **expanded DTD text**, not codes | `JmdictModuleIT` asserts the expansion on purpose ("DTD entity expanded to its description"), so changing it is a design change here, not a bug fix |
| U8 | Open [D65]: check whether the order of `name_entry` arrays and kanji `meanings[]` matters | Before the dictionary service's kanji and names phases |
| U9 | The read-only login is **`dictionary_reader`** (was `api_reader`), created by `docker/postgres/dictionary_reader.sql` [D75] | One name in both stacks and in production. It names its reader, the dictionary service, whose config already defaults to it |

Superseded by the port (were today's fixes on the stale copy): the nested-repository flag
(the ported code uses a top-level `VocabEsRepository`), the `text` gloss / `text[]` stroke
paths (V1 already has them), and the ES version bump in the old compose file.

### docs/adr/0001-retire-in-postgres-delete-from-search.md


Status: accepted (2026-10-06)

When an entry's key disappears from a changed upstream file, the updater deletes its
Elasticsearch document and **retires** its Postgres row (`retired_at` is set, and the row and
its children are kept). Changed entries are updated in place under the same key, never deleted.
Search must stop showing removed entries, such as a JMdict duplicate that was merged away or a
deleted Tatoeba sentence. But the planned API backend will store entry ids in user lists, so the
rows must keep resolving. Retiring also makes a bad run reversible, and the row shows why an
entry disappeared.

#### Rules

- **When:** only at the end of a fully successful changed-file run. A failed or partial run, or a
  hash-skip, retires nothing.
- **Guard:** `updater.<feedId>.retire-guard` (default `0.98`) is the smallest fraction of active
  entries a run may leave active. A file below it retires nothing and logs a WARN, while its
  upserts still apply. A truncated gzip already fails the parse, so the guard only has to catch
  valid-but-wrong files (a partial mirror, a parser bug). Real removals are expected to be far
  under 1% per release. A false block costs little, because stale entries stay searchable until
  someone checks. A loose guard costs a lot: 90% on JMdict would allow ~21,800 removals in one run.
  Every changed-file run logs the would-retire count so the guards can be tuned from data.
  A blocked file is still marked processed, so its removals are re-checked only when upstream
  changes again. Lowering the guard applies from the next changed file.
  For a Kanji sub-source, the guard counts the characters that sub-source has data on, not all
  active kanji rows.
- **Un-retire:** a retired key that comes back is upserted with `retired_at` cleared and
  re-indexed.
- **Order:** Elasticsearch deletes go before the Postgres update. If the update fails, the rows
  are still active, so the next run deletes and retires them again.
- **Kanji:** KanjiDic2, RADKFILE and KanjiVG each own different fields of one row. A sub-source
  that no longer lists a character hard-deletes only its own data (a correction, not a removal).
  The row is retired once no sub-source has data on it.
- **Scope:** a spoke only retires rows of its own source (Tatoeba: `source = 'tanaka-corpus'`).

#### Consequences

- Anything that reads these tables (the future API backend) **must filter
  `retired_at IS NULL`**. Put that in one place there, as a view or a default repository filter,
  so it is hard to forget.
- Retired rows accumulate. Purging long-retired rows that nothing references is a possible later
  cleanup job, not done now.

#### Considered options

- **Hard delete everywhere:** this breaks stored ids and cannot be undone after a bad file.
- **Keep everything (the previous behaviour):** removed entries stay searchable forever.

### docs/adr/0002-open-data-sources-only-no-jreibun.md


Status: accepted (2026-10-06)

The updater only ingests sources with an open licence and a stable public download URL that a
scheduled job can fetch. Jreibun is listed next to Tatoeba under "example sentences" in
`written_reports/Architectures/Container_Diagram_V03.JPG`, but it is out of scope.
Version 1.0 (2026-09-10, 18,877 sentences) is distributed only through GSK: you apply by email
with a signed pledge form, pay ¥33,000 unless you are a GSK member, and receive the files by
manual file share. Use is limited to education, research and non-profit purposes. JMdict's own
examples file (`JMdict_e_examp.gz`, checked 2026-10-06) contains only Tatoeba sentences.

Sources: the Jreibun project page
(https://www.tufs.ac.jp/ts/personal/SUZUKI_Tomomi/jreibun/index-jreibun.html) and the GSK
catalogue entry (https://www.gsk.or.jp/catalog/gsk2026-a/).

#### Consequences

`example_sentence` stays keyed by the Tatoeba Japanese sentence id. The `(source, id)` re-key
that a second sentence source would need is not done. Do it only when an open second source
is added.

### Cross-repo tasks X1–X11, as tracked in the dictionary service until 2026-11-14


Tracked here so they're not forgotten. Do them in the updater repo.

| # | Task | Needed by |
|---|---|---|
| X1 | ✅ _Done in the updater (Flyway V1–V3 from its restored history, V4 for X8; ported 2026-11-14, D73). This repo reuses the migrations (D74)._ Replace `ddl-auto=update` with **Flyway** migrations. The updater owns the schema (D3) | Phase 0 (DDL reuse), schema stability |
| X2 | Add **kuromoji + ICU** multi-field mappings to `vocab` and `example-sentence` (keyword exact / kuromoji tokenized / edge-ngram prefix). Use a custom ES image with the plugins (D14) | Phases 1, 4, 7 (match quality) |
| X3 | Parse and store JMdict `<field>` (subject) and `<dial>` (dialect) tags on `Sense` (same pattern as `miscTags`), then re-import | Phase 8 |
| X4 | ✅ _Done: the ported `NEXT_STEPS.md` is current (D73)._ Update stale `NEXT_STEPS.md` (Kanji + JMnedict spokes are implemented) | housekeeping |
| X5 | (If needed) GIN / `pg_trgm` indexes on `name_entry` arrays via Flyway | Phase 3 |
| X6 | (Later) word-level JLPT source ingestion, once O3 is decided | Phase 8 |
| X7 | ✅ _Done in the updater (V1: sentence text is `text`; ported, D73). This repo's schema, seed and role script no longer handle large objects._ Map `example_sentence.japanese/english/index_words` to `text`, not `@Lob` (which makes Postgres `oid` large objects), e.g. `@JdbcTypeCode(SqlTypes.LONGVARCHAR)`. Do it as a Flyway migration (X1). Then update this repo's `01-schema.sql` and drop the per-LO grants. Reason: large objects need per-object `GRANT`s (no default privileges in Postgres 16), so the read-only role can't read full-load sentences | Phase 4 |
| X8 | _Shipped as updater Flyway **V4** (U3/U4): on an existing DB it drops the unordered rows and forces a JMdict re-import. This repo's `seed/schema.sql` includes V4._ ✅ _Done 2026-11-14 for `sense_gloss`/`sense_pos`/`sense_misc` (`@OrderColumn` in the updater's `Sense`; this repo's `01-schema.sql` regenerated). Still open: X3's tables when they exist, and checking the other spokes' arrays._ **Keep upstream order** for ordered lists. The updater loses it today: `Sense` glosses / POS / misc are `@ElementCollection` lists with no order column, so the order of the JMdict XML (most important gloss first) is not stored. Add `@OrderColumn` (e.g. `gloss_order`) to `sense_gloss`, `sense_pos`, `sense_misc` (and X3's `<field>`/`<dial>` tables) via Flyway (X1), then re-import. **This repo already expects these exact column names** (`gloss_order`, `pos_order`, `misc_order`, 0-based, PK `(sense_id, <x>_order)`); until X8 lands, `validate` fails against a real updater DB. Also check the other spokes for lists whose source order matters (e.g. `name_entry` arrays, kanji `meanings[]`). Readings and senses are fine: identity ids follow insert order. Then update this repo's `01-schema.sql` and add the matching `@OrderColumn` in `vocabulary/models/Sense` | Phase 1 (gloss display order) |
| X9 | _Superseded 2026-11-14 by the port (D73): the ported code already had its own fixes (top-level `VocabEsRepository`, `text` columns in V1, ES 9). Kept as a record of what the Docker run found._ ✅ _Done 2026-11-14_ (found by the Docker run). (a) The app could not start: the ES repositories are nested interfaces inside the `*IndexWriter` facades, which Spring Data skips by default, so the fix is `@EnableElasticsearchRepositories(considerNestedRepositories = true)`. (b) The compose ES was 8.15.3, but Boot 4.1's client is 9.x, and ES 8 rejects its `compatible-with=9` header (400), so ES is now 9.4.8 (D57). (c) Full loads failed on `varchar(255)`: one JMdict gloss is 354 chars and KanjiVG stroke paths are up to 377, so `sense_gloss.gloss` is now `text` and `kanji.stroke_paths` is `text[]` (`length = Length.LONG32`). An existing updater DB needs `ALTER ... TYPE text` (no Flyway yet, X1) | Phase 0/1 (full load) |
| X10 | _Note (2026-11-14): the updater asserts the expansion on purpose (`JmdictModuleIT`, "DTD entity expanded to its description", U7), and this repo's fixtures now use the expanded text (D77). Changing it is a design decision in both repos._ Store JMdict **entity codes** for POS/misc (`n`, `v1`, `exp`, `sl`), not the expanded DTD text (`noun (common) (futsuumeishi)`). The parser resolves the inline DTD entities today. Codes are short, stable, and what Phase 7 tag search and Phase 8 lists (`exp`, slang) want; the display text can be a lookup table. Needs a re-import | Phases 7, 8 |
| X11 | _Mostly resolved by the port: the GitHub API call is gone (KanjiVG's latest release comes from the `releases/latest` redirect), and failures now carry status, rate-limit headers and body (D69, U6). Pinning `updater.kanjivg.url` in production is still an option._ (Optional) KanjiVG's "latest release" lookup calls the GitHub API without a token, so it can get **HTTP 403** (rate limit; seen once 2026-11-14, fine on retry). Pin `updater.kanjivg.url` or send a token in production | Reliability |

## 2026-11-23 — Task lists moved to REQUIREMENTS.md (U17)

These PLAN.md sections left for the app-wide `REQUIREMENTS.md` in the dictionary service, to
keep one task list (its D95). Moved here word for word. Where each item went:
Cognito → R13.2, R13.3; image, CI → R15.1; one task → R15.2, S12; secrets → R15.3; cron time
zone → R12.1; memory → R15.5; network, KanjiVG pin → R15.6; RDS reader, ES keys → R15.4;
monitoring design → open question; overlapping runs → R12.6; local ES auth → S25; retire-guard
tuning → R12.7; radical cleanup → S10; bulk upserts → S8; config server → S26; container
diagram → S27; `hibernate.dialect` and Boot 4.1.2 → S28.

## Go live on Cognito

- [ ] Create the user pool, an app client for admin callers, and the group `updater-admin`.
- [ ] Set `COGNITO_ISSUER_URI` and `COGNITO_CLIENT_IDS` (the `prod` profile reads them).
- [ ] Smoke test with an **access** token: 200 for a group member, 403 for a non-member, 401
  without a token. ID tokens are rejected by design.
- [ ] Once no environment uses the dev token, delete `hub/StubJwtDecoderConfig` and the
  `updater.admin.dev-token` / `stub-auth` properties.

## AWS deployment (not started)

- [ ] Image: build the `Dockerfile`, push to ECR. The image build skips tests, so CI runs
  `./mvnw verify` first (Testcontainers needs Docker).
- [ ] Runtime, e.g. ECS Fargate, with **exactly one** task: the cron runs in-process, so two
  tasks would run every feed twice. More tasks need a lock (ShedLock) or an EventBridge schedule.
- [ ] Secrets: `DB_*`, `ELASTICSEARCH_*`, `COGNITO_*` from Secrets Manager / SSM as env vars.
- [ ] Cron time zone: the container is UTC, so `0 0 3 * * *` is 03:00 UTC.
- [ ] Memory: the JVM takes 75% of the container. Size it from a measured JMdict run.
- [ ] Network: outbound HTTPS to EDRDG, GitHub (KanjiVG) and Tatoeba; inbound only from admin
  callers. Consider pinning `updater.kanjivg.url`, since GitHub can rate-limit (once seen as a 403).
- [ ] RDS: the datasource user owns the schema. Create `dictionary_reader` through infra, with
  the grants from `docker/postgres/dictionary_reader.sql` and a real password.
- [ ] Elastic Cloud: one write API key for the updater (`vocab`, `example-sentence`), a separate
  read-only key for the dictionary service.

## Other open work

- [ ] Monitoring, tracing, logging: needs its own design session. Decided so far: U16.
  Open: OpenTelemetry vs Elastic APM, log shipping, JSON logs, dashboards.
- [ ] Overlapping runs: a cron run can overlap a manual run of the same feed (different threads).
- [ ] Local ES has no auth, so the dictionary service could write to it locally. A read-only ES
  user needs `xpack.security.enabled=true`.
- [ ] Tune each feed's `retire-guard` from the logged retirement counts after a few real releases
  (first run: about 0.0005% per feed, far below the 2% the guard allows).
- [ ] Radicals that RADKFILE drops stay in `radical`. A cleanup job for long-retired,
  unreferenced rows is possible later.
- [ ] Run time: a changed JMdict file takes ~10 min, mostly row-by-row statements. If that
  matters, switch to `SEQUENCE` ids and bulk upserts.
- [ ] A config server may replace the duplicated `local` / `docker` property files.
- [ ] The container diagram (in the dictionary service's `written_reports/`) says MySQL. Add the
  local stack and the separate observability ES.
- [ ] Cleanup: remove `hibernate.dialect` (detected automatically); upgrade to Boot 4.1.2.

Decision:
- **U17 — one task list.** Asked whether the updater should follow the services repo's
  workflow. Options offered: move the task lists into REQUIREMENTS.md and keep the committed
  `X<n>` specs here (advised). Chosen as advised; the README was reordered to match.

## 2026-11-23 — X8 done, X12 added, U18

X8 is done: kanji arrays and the `name_entry` arrays keep upstream order (the parser appends
`keb`, `reb` and `trans_det` in file order and keeps the first of each repeated name type).
X3's future tables get order columns as part of X3. Its text, as it stood in PLAN.md:

- [ ] **X8** — Check whether the order of `kanji.meanings[]` and the `name_entry` arrays matters
  and is kept. If it is lost, add order columns in a new migration. Needed before the service's
  kanji and names phases. (The sense lists already keep order: U3.)

X12 was added from the dictionary service's grilling session (its D97, which has the options
and reasoning). U18 mirrors the service's D96: updater changes are allowed from its sessions,
with the guardrails written there.


## 2026-11-23 — X12 done (built without Docker)

The tatoeba spoke now writes two keyword arrays to the `example-sentence` document: `headwords`
(each token's headword with `(reading)`, `[nn]`, `{form}` and `~` removed, in order, duplicates
dropped) and `exampleFor` (headwords of `~` tokens). `indexWords` and the Postgres table are
unchanged; no migration. `TatoebaParserTest` passes; `TatoebaModuleIT` compiles but was not run
(no Docker), so the service's R14.8 is 🔵 until the `tatoeba` feed is re-run. Its text, as it
stood in PLAN.md:

- [ ] **X12** — Sentence headwords, for the service's R4.2 (its D97). The tatoeba spoke parses
  `index_words` and adds two keyword arrays to the `example-sentence` document: `headwords`, each
  token's headword with `(reading)`, `[nn]`, `{form}` and `~` removed, in order, duplicates
  dropped; and `exampleFor`, the headwords of tokens marked `~`. `indexWords` stays as it is; no
  Postgres change. `TatoebaParserTest` covers `其の[01]{その}`, `猫(ねこ)[01]`, `食べる{食べない}`
  and a `~` token. Re-run the `tatoeba` feed. The service keeps its own copy of the mapping.
