# CLAUDE.md — japanese-dictionary-updater-component

The updater: the only writer of the dictionary data. The read-only API that serves it is in
`../japanese-dictionary-services`, which has its own `CLAUDE.md` with the same rules.

## Reading

- **Start of a session:** read "Next session" at the top of `docs/PLAN.md`.
- **How something works** (hub and spokes, runs, retirement, data caveats, glossary): `docs/CONTEXT.md`.
- **Profiles, properties, env vars, logins:** `docs/configuration.md`.
- **What the whole app must do** (all four applications):
  `../japanese-dictionary-services/written_reports/REQUIREMENTS.md`. App-wide docs live in that
  repo (its D92); updater requirements there name the spoke or `hub`.
- **Running, the admin API, tests:** `README.md`.
- **An id that `docs/PLAN.md` doesn't list, or the user asks about the past:** `docs/HISTORY.md`.
  It is the archive; leave it closed otherwise.

## The docs

One purpose per file, each with a budget. Before adding past a budget, cut first.

| File | Holds | Budget |
|---|---|---|
| `CLAUDE.md` | These rules and pointers | 80 lines |
| `README.md` | How to restart and run | 100 lines |
| `docs/CONTEXT.md` | What the code can't say: why, rules, caveats | 8 KB |
| `docs/PLAN.md` | Next session, open tasks, decisions in force | 12 KB |
| `docs/configuration.md` | Profiles and properties reference | 6 KB |
| `docs/HISTORY.md` | Done tasks, superseded decisions, verification notes | none |

- **A fact** about how things work goes to CONTEXT.md. **A task, status or decision** goes to PLAN.md.
- **Point to the code** for anything it already says (schema, config, versions, class layout):
  name the file and add only what the file doesn't show.
- **Write sentences that read on their own.** Ids go at the end as a reference, like "(U11)".
- **Decisions** get the next free `U<n>`, one line in PLAN.md's table. Ids are permanent.
- **Done or superseded items** move to HISTORY.md under a dated heading, word for word, and
  leave PLAN.md. HISTORY.md is append-only.
- **Cross-repo decisions** live in the repo whose code or data they change. The other repo links
  to it. Tasks the dictionary service waits on are `X<n>`, described in full here.
- **Code comments** explain only what the code can't: a non-obvious reason or a gotcha.
- Bump `_Last updated_` in each doc you change.

## Working

- **Schema changes are new migrations** (`V<n>__*.sql`). Applied migrations stay byte-for-byte as
  they are: Flyway checksums them, comments included. Afterwards, the dictionary service runs
  `sh docker/generate-schema.sh`; say so in the task report.
- **Open decisions:** give your advice, then let the user choose with a question.
- **Update the docs along with the code**, in the same change.
- **The user makes every commit.** At the end of each unit of work, stop with a suggested commit
  message, wait for "committed", then check `git status` before continuing.
- **End every task** with:
  - `Docs:` and the files updated, or why none needed it.
  - `Choices I made:` the small implementation choices, in the chat only. They go into the docs
    only when the user says "log that".
- **End of a session:** rewrite "Next session" (15 lines max) so the next session can start from
  it alone.
- **When the user corrects how docs are written,** add the correction to this file.
