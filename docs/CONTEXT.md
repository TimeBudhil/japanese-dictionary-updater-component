# Updater — Context

How the updater works and the rules it follows: only what the code can't tell you.
Tasks and decisions: [`PLAN.md`](./PLAN.md). Profiles and properties:
[`configuration.md`](./configuration.md). Running it: [`README.md`](../README.md).

_Last updated: 2026-11-14_

## What it is

Keeps a Postgres copy of the open Japanese dictionary sources up to date, plus a search copy in
Elasticsearch. It is the **only writer** of that data. The dictionary service
(`../japanese-dictionary-services`) reads it through the read-only login `dictionary_reader`.
The two never call each other.

## Shape: hub and spokes

- `hub/`: shared machinery. The scheduler and admin API, feed version tracking
  (`source_version`, one content hash per feed), retirement, security, streaming XML.
- `spokes/<source>/`: one module per source. Each has `fetch/` (download), `parse/`
  (stream the file), `domain/` (entities + repositories) and, where searched, `search/`
  (the ES document and index writer).

| Spoke id | Feeds | Writes |
|---|---|---|
| `jmdict` | `jmdict` (JMdict_e) | `vocab_entry`, `reading`, `sense`, `sense_*`; ES `vocab` |
| `jmnedict` | `jmnedict` | `name_entry` |
| `kanji` | `kanji.kanjidic2`, `kanji.radkfile`, `kanji.kanjivg` | `kanji`, `radical`, `kanji_radical` |
| `tatoeba` | `tatoeba` | `example_sentence`; ES `example-sentence` |

Schema: `src/main/resources/db/migration/V*.sql`. ES documents: `spokes/*/search/`.

## How a run works

- A run is started by the cron (`0 0 3 * * *`, UTC in the container), by the admin API, or on
  startup (`docker` profile only). Admin-API and startup runs share one background thread,
  so they queue; the cron uses the scheduler thread.
- Per feed: download, hash, and **skip if the hash is unchanged**. Otherwise parse and upsert
  every entry under its stable key, and index the searched ones.
- Re-running is safe: the same file gives the same rows.

## Retirement (U11)

An entry whose key disappears from a changed file is **retired**: its ES document is deleted
and its Postgres row gets `retired_at`. The row and its children stay, so stored ids still
resolve. If the key comes back, the row is un-retired and re-indexed.
- Only at the end of a **fully successful** changed-file run. A failed run or a hash-skip
  retires nothing.
- **Guard:** `updater.<feedId>.retire-guard` (default `0.98`) is the smallest share of active
  entries a run may leave active. A file below it retires nothing and logs a WARN, while its
  upserts still apply. It catches valid-but-wrong files (a partial mirror, a parser bug).
  Every changed-file run logs the would-retire count.
- **Order:** ES deletes go before the Postgres update, so a failed update is redone next run.
- **Kanji:** the three sub-sources each own different fields of one row. A sub-source that
  drops a character deletes only its own data. The row is retired once no sub-source has it.
- **Scope:** a spoke retires only its own source's rows (Tatoeba: `source = 'tanaka-corpus'`).

## Data caveats

- Gloss, POS and misc lists keep JMdict order through `*_order` columns (U3).
- POS and misc are stored as the expanded DTD text (`noun (common) (futsuumeishi)`), on purpose
  (`JmdictModuleIT`). Codes are an open question (X10). `<field>` and `<dial>` aren't stored (X3).
- `example_sentence` is keyed by the Japanese Tatoeba sentence id.
- Free text and lists are `text` / `text[]` with no length limit
  (`UpdaterComponentApplicationIT.freeTextColumnsAreNotLengthLimited`).

## Stack

Java 26, Spring Boot 4.1.1, Flyway, Spring Data JPA + Elasticsearch (9.x client), Spring
Security as an OAuth2 resource server (Cognito in `prod`, a stub dev token locally),
Testcontainers. Package `badee` (the dictionary service uses `baddee`, on purpose).

## Glossary

| Term | Meaning | Avoid |
|---|---|---|
| Feed | One upstream file tracked by content hash (`jmdict`, `kanji.radkfile`) | source file, dataset |
| Spoke | The module that fetches, parses and stores one source; may own several feeds | plugin, connector |
| Sub-source | One of the three kanji feeds; each owns its own fields of a kanji row | |
| Entry | One top-level record under its stable upstream key (ent_seq, Tatoeba id, kanji character) | item, record, word |
| Active entry | An entry the latest processed file of its feed still contains | |
| Retired entry | An entry whose key left upstream; row kept, gone from search | deleted, archived |
| Retire guard | The smallest share of active entries one run may leave active | |
