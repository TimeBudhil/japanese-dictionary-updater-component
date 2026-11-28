# Updater — Plan

What's next, the `X<n>` specs the dictionary service codes against, and the decisions in force.
Every other task is a row in the app-wide
[`REQUIREMENTS.md`](../../japanese-dictionary-services/docs/REQUIREMENTS.md) (updater
rows R12–R15, S8–S12, S25–S28), whose status tags track progress (U17). How things work: [`CONTEXT.md`](./CONTEXT.md).
Done work and old decisions: [`HISTORY.md`](./HISTORY.md) (not read by default).

_Last updated: 2026-11-23_

## Next session

- **Where we are:** all four spokes work and are tested. The schema is Flyway V1–V5. Not deployed.
- **Next:** X5 and X12 are built without Docker (HISTORY.md); their ITs still need a Docker run. The dictionary service's session builds
  them here and then its side, updater committed first (U18). Without Docker they reach 🔵. With
  Docker, the service's phase 01 doc (`docs/phases/01-name-search-and-integration-tests/phase.md`, Further Notes) has the snapshot and seed steps.
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
| U17 | Tasks other than `X<n>` specs live as rows in the services repo's `docs/REQUIREMENTS.md`; PLAN.md keeps Next session, `X<n>` specs and decisions (mirrors its D95) |
| U18 | The dictionary service's sessions may change this repo when one of its requirement rows needs it: tracked by its `X<n>`, committed here first, U13 still holds (mirrors its D96) |
