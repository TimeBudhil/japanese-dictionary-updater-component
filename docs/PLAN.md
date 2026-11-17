# Updater — Plan

What's next, what's left, and the decisions in force. How things work: [`CONTEXT.md`](./CONTEXT.md).
Done work and old decisions: [`HISTORY.md`](./HISTORY.md) (not read by default).

_Last updated: 2026-11-16_

## Next session

- **Where we are:** all four spokes (JMdict, JMnedict, Kanji, Tatoeba) work and are tested. The
  schema is Flyway V1–V4. `main` has the full history. Not deployed anywhere yet.
- **Docs system (U10) is in place** in both repos; its rules are in `CLAUDE.md`.
- **Nothing here blocks the dictionary service's next phase (kanji).** Before its kanji and names
  phases, do the order check in X8.
- Housekeeping: an old stash `docker-verification-2026-11-14` on `main` holds dropped fixes; it can
  be deleted with `git stash drop`.

## Tasks for the dictionary service

The dictionary service lists these under "Waiting on the updater". Ids are shared by both repos.

- [ ] **X8** — Check whether the order of `kanji.meanings[]` and the `name_entry` arrays matters
  and is kept. If it is lost, add order columns in a new migration. Needed before the service's
  kanji and names phases. (The sense lists already keep order: U3.)
- [ ] **X2** — kuromoji + ICU multi-fields on `vocab` and `example-sentence` (keyword exact,
  tokenized, edge-ngram prefix), plus a keyword subfield on `glosses` so English gets an exact
  match. Needs an ES image with the plugins.
- [ ] **X3** — Parse and store JMdict `<field>` (subject) and `<dial>` (dialect) tags on `Sense`,
  like misc, with order columns. Re-import.
- [ ] **X10** — Decide: store POS/misc as JMdict codes (`n`, `v1`, `sl`) instead of the expanded
  text. `JmdictModuleIT` asserts the expansion on purpose, so this is a design change in both
  repos. Needs a re-import.
- [ ] **X5** — Name search indexes. Needed now: unindexed, a names search page takes ~3 s on the
  full 743k rows; with these, 3–40 ms (a lone kana ~200 ms; measured 2026-11-16). The service's Phase 3 queries already
  call the two functions by these exact names, so its name search fails until this migration
  runs. Add as a new migration, verbatim:
  ```sql
  CREATE EXTENSION IF NOT EXISTS pg_trgm;
  -- Values joined by spaces, with a leading space, so `LIKE '% q%'` is a word-start match
  -- that the trigram index serves even for 2-character queries (田中). array_to_string is
  -- only STABLE, so it can't be indexed directly; these wrappers are IMMUTABLE.
  CREATE FUNCTION name_entry_jp_text(kanji text[], readings text[]) RETURNS text
      LANGUAGE sql IMMUTABLE PARALLEL SAFE
      AS $$ SELECT ' ' || array_to_string(kanji || readings, ' ') $$;
  CREATE FUNCTION name_entry_en_text(translations text[]) RETURNS text
      LANGUAGE sql IMMUTABLE PARALLEL SAFE
      AS $$ SELECT ' ' || lower(array_to_string(translations, ' ')) $$;
  CREATE INDEX name_entry_jp_text_idx ON name_entry USING gin (name_entry_jp_text(kanji, readings) gin_trgm_ops);
  CREATE INDEX name_entry_en_text_idx ON name_entry USING gin (name_entry_en_text(translations) gin_trgm_ops);
  ```
  The two indexes take ~90 MB on the full load. The service then regenerates its
  `schema.sql` (its D90, D91).
- [ ] **X6** — A word-level JLPT source, once the service decides which list (its O3).

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

## Decisions in force

Full reasoning for each id: `HISTORY.md`.

| # | Decision |
|---|---|
| U2 | The compose stack lives in this repo. ES server 9.4.8, the same as the dictionary service |
| U3 | Gloss, POS and misc lists keep JMdict order through order columns (V4) |
| U9 | The read-only login is `dictionary_reader`, in every environment |
| U10 | Docs system: `CLAUDE.md` rules, one purpose per file, size budgets, `HISTORY.md` archive. A cross-repo decision lives in the repo it changes. Small choices are reported in chat only |
| U11 | Entries removed upstream are **retired**, not deleted: the row stays with `retired_at`, the ES document is deleted. Readers decide how to show retired rows; the dictionary service returns them by id and never in search (its D76) |
| U12 | Only openly licensed sources with a public download. Jreibun is not ingested |
| U13 | Flyway owns the schema; Hibernate only validates. Never edit an applied migration, add a new one |
| U14 | No default profile: `local`, `docker` or `prod` must be chosen, so a missing setting fails fast |
| U15 | Production search is Elastic Cloud (ES 9.x); AWS OpenSearch doesn't work with the 9.x client |
| U16 | Observability gets its own Elasticsearch, never the search cluster |
