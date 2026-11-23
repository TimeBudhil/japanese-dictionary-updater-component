# Updater — Plan

What's next, the `X<n>` specs the dictionary service codes against, and the decisions in force.
Every other task is a row in the app-wide
[`REQUIREMENTS.md`](../../japanese-dictionary-services/written_reports/REQUIREMENTS.md) (updater
rows R12–R15, S8–S12, S25–S28), whose status tags track progress (U17). How things work: [`CONTEXT.md`](./CONTEXT.md).
Done work and old decisions: [`HISTORY.md`](./HISTORY.md) (not read by default).

_Last updated: 2026-11-23_

## Next session

- **Where we are:** all four spokes work and are tested. The schema is Flyway V1–V4. Not deployed.
- **Next:** X5 (as `V5__name_search.sql`), below. X12 is built (HISTORY.md). The dictionary service's session builds
  them here and then its side, updater committed first (U18). Without Docker they reach 🔵. With
  Docker, the service's `docs/PLAN.md` "Next session" has the snapshot and seed steps.
- Housekeeping: an old stash `docker-verification-2026-11-14` on `main` holds dropped fixes; it can
  be deleted with `git stash drop`.

## Tasks for the dictionary service

Committed specs the dictionary service depends on. Ids are shared by both repos; the services
repo's REQUIREMENTS.md marks each blocked row "blocked by X<n>" (R14 lists them).

- [ ] **X2** — kuromoji + ICU multi-fields on `vocab` and `example-sentence` (keyword exact,
  tokenized, edge-ngram prefix), plus a keyword subfield on `glosses` so English gets an exact
  match. Needs an ES image with the plugins.
- [ ] **X3** — Parse and store JMdict `<field>` (subject) and `<dial>` (dialect) tags on `Sense`,
  like misc, with order columns. Re-import.
- [ ] **X10** — Decide: store POS/misc as JMdict codes (`n`, `v1`, `sl`) instead of the expanded
  text. `JmdictModuleIT` asserts the expansion on purpose, so this is a design change in both
  repos. Needs a re-import.
- [ ] **X5** — Name search indexes. Needed now: unindexed, a names search page takes ~3 s on the
  full 743k rows; with these, 3–40 ms (a lone kana ~200 ms; measured 2026-11-16). The service's name search (its R3.2) already
  calls the two functions by these exact names, so its name search fails until this migration
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
  The two indexes take ~90 MB on the full load. An IT asserts that both functions exist after
  Flyway. The service then regenerates its `schema.sql` (its D90, D91).
- [ ] **X6** — A word-level JLPT source, once the service decides which list (its O3).

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
| U17 | Tasks other than `X<n>` specs live as rows in the services repo's `written_reports/REQUIREMENTS.md`; PLAN.md keeps Next session, `X<n>` specs and decisions (mirrors its D95) |
| U18 | The dictionary service's sessions may change this repo when one of its requirement rows needs it: tracked by its `X<n>`, committed here first, U13 still holds (mirrors its D96) |
