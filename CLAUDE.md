# CLAUDE.md — japanese-dictionary-updater-component

The updater: the only writer of the dictionary data. The read-only API that serves it is in
`../japanese-dictionary-services`, which has its own `CLAUDE.md` with the same rules.

## Reading

Read as little as the task needs.
- **Start of a session:** "Next session" at the top of `docs/PLAN.md`.
- **What to build:** `../japanese-dictionary-services/docs/REQUIREMENTS.md`, **only
  the section or R-IDs** for the task (grep the id; updater rows name the spoke or `hub`).
  Never read the whole file. App-wide docs live in that repo (its D92).
- **How something works** (hub and spokes, runs, retirement, data caveats, glossary): `docs/CONTEXT.md`.
- **Profiles, properties, env vars, logins:** `docs/configuration.md`.
- **Running, the admin API, tests:** `README.md`.
- **An id PLAN.md doesn't list, or the user asks about the past:** `docs/HISTORY.md`, by grep
  only. It is the archive; leave it closed otherwise.

## The docs

One purpose per file, each with a budget. Before adding past a budget, cut first.

| File | Holds | Budget |
|---|---|---|
| `CLAUDE.md` | These rules and pointers | 80 lines |
| `README.md` | Index of the docs, how to restart and run | 100 lines |
| `docs/PLAN.md` | Next session, `X<n>` specs, decisions in force. Other tasks are REQUIREMENTS.md rows (U17) | 12 KB |
| `docs/CONTEXT.md` | What the code can't say: why, rules, caveats | 8 KB |
| `docs/configuration.md` | Profiles and properties reference | 6 KB |
| `docs/HISTORY.md` | Done work, superseded decisions, verification notes. Append-only | none |

## Keeping the docs current

Update the docs in the same change as the code. After any change, apply every row that fits:

| When this happens | Update |
|---|---|
| A requirement gets built | Its status tag in the services repo's REQUIREMENTS.md: `🔵` built without Docker, `✅` checked on real data |
| An `X<n>` task is done | Move it from PLAN.md to HISTORY.md word for word, dated; update its requirement row |
| A requirement is added or changed | REQUIREMENTS.md in the services repo, next free id; ids never change |
| A decision is made | One line in PLAN.md's table, next free `U<n>`; the options and reasoning go to HISTORY.md |
| A decision is superseded | Move its row to HISTORY.md word for word, dated |
| You learn how something works, or a data caveat | CONTEXT.md |
| A profile, property or env var changes | `docs/configuration.md` |
| Run steps, ports or the admin API change | README.md |
| A new doc or folder | A row in README.md "The docs", and a return step if a returning reader needs it |
| A new task (not an `X<n>` spec) | A row in REQUIREMENTS.md, never a task list in PLAN.md |
| A schema migration is added | Say in the task report that the services repo runs `sh docker/generate-schema.sh` |
| A spec is written | the phase doc `../japanese-dictionary-services/docs/phases/NN-slug/phase.md`, with its tasks in `tasks/` beside it |
| A session ends | Rewrite PLAN.md "Next session" (15 lines max) so the next session can start from it alone |

Bump `_Last updated_` in each doc you change.

## Writing

- **README stays a lightweight index:** a link to every doc with its path, then how to run.
  Section order is fixed: "The docs", "Coming back after a while", "Run it", then "Tests" and
  "Schema changes".
- **Point to the code** for anything it already says (schema, config, versions, class layout).
- **Write sentences that read on their own.** Ids go at the end as a reference, like "(U11)".
- **Cross-repo decisions** live in the repo whose code or data they change; the other links to
  it. Tasks the dictionary service waits on are `X<n>`, described in full in PLAN.md here.
- **Code comments** explain only what the code can't: a non-obvious reason or a gotcha.
- **No phases.** Name work by its R-ids or `X<n>`.

## Working

- **Schema changes are new migrations** (`V<n>__*.sql`). Applied migrations stay byte-for-byte as
  they are: Flyway checksums them, comments included.
- **Open decisions:** give your advice, then let the user choose with a question.
- **Claude may commit, but only on feature branches.** Commit attribution is off, so commits show
  the user as author. Never commit to `main`; the user merges into `main` after review.
- **End every task** with:
  - `Requirements:` the R-IDs whose status changed, or "none".
  - `Docs:` the files updated, or why none needed it.
  - `Choices I made:` the small implementation choices, in the chat only. They go into the docs
    only when the user says "log that".
- **When the user corrects how docs are written,** add the correction to this file.
