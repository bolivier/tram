# Instructions for AI Agents

Guidance for AI agents working in this repository. This is the single source of
truth; `CLAUDE.md` is a symlink to this file.

## Project Overview

Tram is an opinionated Clojure web framework (early alpha) for rapid application
development, inspired by Ruby on Rails. It wraps Toucan2 (ORM), Reitit
(routing), Selmer + huff2 (templates/HTML), and Integrant (dependency injection)
into a batteries-included stack, plus a `tram` CLI for scaffolding.

The design goal: sensible defaults where every component is easy to extend or
swap for your own version.

## Gotchas (read first)

These are the things most likely to trip you up. They override your defaults.

- **Toucan2 is a git dep pinned to camsaul's canonical repo**, not the local
  `../toucan2` checkout and not Clojars. Don't "fix" the coordinate in
  `deps.edn`.
- **Extension points use protocols; failing that, multimethods** — and external
  multimethods use Methodical, not `clojure.core`. See
  [Architecture](#architecture).
- **Trivial queries use the Toucan2 ORM; anything more goes through HoneySQL.**
  See [Database](#database).
- **Comments describe _why_, never _what_.** See [Code Style](#code-style).
- **A handler's view is resolved from the handler's name.** `defroutes` maps
  handler `foo` in `app.handlers.bar-handlers` to view `foo` in
  `app.views.bar-views`. Pass `:template` only when the two names differ.
- **Keep the starter template in sync.** A framework change that affects
  generated apps must be mirrored in `starter-template/`.

# Writing style

These rules cover every word you write: chat replies, code comments, commit messages, PR
descriptions, Jira tickets, and docs. They override your default response style. Follow them
even when the default style tells you otherwise.

Check each message against this list before you send it:

1. Keep sentences to 20 words or less. Split long sentences into two.
2. Do not use em-dashes. Use a period or a comma.
3. Keep paragraphs to 3 sentences or less. One topic per paragraph.
4. Use active voice. Write "Run the test", not "The test should be run".
5. Use one word for one idea. Do not switch between synonyms in the same document.
6. Cut hedges: "might want to", "it seems", "I think", "probably", "just", "simply".
7. Cut preambles: "Let me", "I'll go ahead and", "Great question", "You're right".
8. Cut summary paragraphs that repeat what you said above.

Use the word on the left. Do not use the words on the right.

| Use     | Do not use                        |
| ------- | --------------------------------- |
| use     | utilize, leverage                 |
| start   | initiate, kick off, spin up       |
| change  | modify, adjust, tweak, tune       |
| show    | surface, expose, highlight        |
| find    | identify, locate, discover        |
| fix     | address, resolve, handle          |
| add     | introduce, wire up, hook up       |
| check   | verify, validate, confirm, ensure |
| because | given that, in light of, as such  |

Examples:

- Write "The test fails because the mock returns null."
  Not "It appears the test may be failing due to the mock returning a null value."
- Write "I added the column and ran the migration."
  Not "I went ahead and added the column — after that, I ran the migration."
- Write "Run `npm run swc`. It skips type checks."
  Not "You might want to consider running `npm run swc`, which is faster since it
  skips type checking."


## Architecture

Points of extension should use protocols where possible, and multimethods where
not.

External multimethods should use Methodical by default — it supports CLOS-style
dispatch and `:before`/`:after`/`:around` methods, which make extension far
easier. Internal-only multimethods can use `clojure.core` multimethods.

## Repository Layout

Framework code lives under `tram.*`; testing utilities under `rapid-test.*`; the
CLI under `tram-cli.*`.

| Path                                   | What                                             |
|----------------------------------------|--------------------------------------------------|
| `src/main/tram/core.clj`               | Main public API (re-exports via `import-vars`)   |
| `src/main/tram/db.clj`                 | Database operations                              |
| `src/main/tram/routes.clj`             | Routing macros and interceptors                  |
| `src/main/tram/html.clj`               | HTML utilities and path generation               |
| `src/main/tram/associations.clj`       | ORM associations (`has-many!`, etc.)             |
| `src/main/tram/rendering/`             | Template rendering                               |
| `src/main/rapid_test/`                 | Test helpers (HTML assertions, hiccup zipper)    |
| `src/cli/tram_cli/`                    | `tram` CLI (new, start, test, db:migrate, dev)   |
| `src/bb_compatible/`                   | Code that must run under Babashka                |
| `test/main/tram/test_fixtures.clj`     | Test setup, fixtures, sample data                |
| `starter-template/`                    | Scaffold for new Tram apps — keep in sync        |
| `deps.edn`                             | Dependencies and aliases                         |
| `tests.edn`                            | Kaocha test runner config                        |
| `tram.edn`                             | Framework config (DB connection, etc.)           |
| `./tram`                               | CLI entrypoint (`bb -m tram-cli.entry`)          |

## Tech Stack

| Concern      | Library                                                    |
|--------------|------------------------------------------------------------|
| ORM          | Toucan2 (git dep, camsaul canonical)                       |
| Complex SQL  | HoneySQL                                                    |
| DB access    | next.jdbc; PostgreSQL (dev/prod), SQLite (test)            |
| Migrations   | Migratus                                                    |
| Routing      | Reitit                                                      |
| HTTP server  | http-kit                                                    |
| Templates    | Selmer; huff2 (hiccup → HTML, bolivier fork)               |
| DI/lifecycle | Integrant + integrant-repl                                 |
| Schema       | Malli                                                       |
| Multimethods | Methodical (extensible dispatch)                           |
| Auth         | buddy                                                       |
| Config       | Aero                                                        |
| Logging      | Telemere (+ slf4j-timbre)                                  |
| Inflection   | declensia (pluralization for Rails-style naming, bolivier) |
| Test runner  | Kaocha + matcher-combinators                               |

## Development Commands

Most REPL-driven work happens through the Clojure MCP server (see below). Shell
commands:

```sh
# Tests (Kaocha)
bin/test                 # run all tests
clojure -M:test          # same, directly
clojure -M:test --watch  # watch mode
bin/test-bb              # Babashka-compatible tests

# Formatting (zprint) & linting (clj-kondo)
zprint '{:search-config? true}' -w src/**/*.clj   # format in place
zprint '{:search-config? true}' -c src/**/*.clj   # check only
bin/lint                                          # lint src + test
bin/copy-lint-configs                             # import lint configs from deps

# CLI (scaffolding)
./tram new <name>          # create a new project in the current directory
```

### Aliases vs. scripts

`deps.edn` aliases exist to assemble dependencies and classpath — the ecosystem a
tool needs. Keep specific invocations (lint targets, flags, arg lists) out of an
alias's `:main-opts`; put them in a `bin/` script that calls the tool. This keeps
the alias reusable by more than one caller — e.g. `:clj-kondo` just provides the
linter, while `bin/lint` and `bin/copy-lint-configs` invoke it with their own args.

A self-contained, single-purpose runner (`:test`, `:cli`) is the tolerated
exception. But the moment you want to invoke an alias two different ways, that's
the signal to move the invocation into a script.

### Clojure MCP Server (preferred for REPL work)

Use the Clojure MCP server (nREPL) to evaluate code, run tests interactively,
and inspect state. If no nREPL server is running, prompt the user to start one
before doing REPL-dependent work.

## Database

- Tests use SQLite (`resources/test.db`) — no external DB needed. Dev/prod use
  PostgreSQL or SQLite.
- Toucan2 is the ORM. Prefer its API (`select`, `insert!`, `delete!`,
  `update!`) for trivial SQL only. Anything more complex than a simple join, or
  needing specific performance characteristics, should use HoneySQL.
- Migrations are managed by Migratus (test migrations in
  `resources/test-migrations/`).
- Associations: `has-many!`, `has-one!`, `belongs-to!` macros in
  `tram.associations`.

## Testing

`rapid-test.*` provides HTML/hiccup assertion helpers. See
`test/main/tram/test_fixtures.clj` for fixtures and sample data.

## Code Style

### Comments

**Never write a comment that describes _what_ the code does.** Code should be
self-documenting through intent-revealing names and small functions. The only
acceptable comments are:

1. Type/doc annotations.
2. A single line of out-of-band context the code cannot convey (a non-obvious
   _why_, a surprising external constraint).

### Clojure Idioms

- Prefer `map`/`filter`/`reduce` over manual recursion.
- Use threading macros (`->`, `->>`) for transformation pipelines.
- Prefer pure functions; isolate side effects at the edges.
- Destructure in argument lists and `let` bindings — one level deep only.
- Group arguments that travel together into one map, destructured at the
  boundary; give the shape a Malli schema if it recurs.
- Use Malli schemas to validate and check values rather than ad hoc checks.
- Predicates end with `?`; mutating functions end with `!`.
- kebab-case for Clojure names, snake_case for database columns.

### Naming Conventions

- Framework namespaces: `tram.*`. Test utilities: `rapid-test.*`. CLI:
  `tram-cli.*`.
- Models use keyword identifiers: `:models/users`, `:models/accounts`.
- Routes use keyword names: `:route/dashboard`, `:route/user`.
- Namespaces with `^:public` metadata are public API.

### Structure

How code should be shaped. Each rule carries a _watch for_ — the signal that it
is being broken.

- **Names reveal intent.** A name says what the thing does or holds.
  _Watch for:_ a name that doesn't; if no honest name comes, the design is
  murky.
- **One home for a logic shape.** The same logic lives in one place, called from
  everywhere that needs it.
  _Watch for:_ the same shape in two or more places — including the same value
  validated ad hoc in two or more places, which wants a Malli schema.
- **Things that change together live together.** One concern's code sits in one
  place.
  _Watch for:_ one logical change forcing scattered edits across many files.
- **A namespace changes for one reason.** Split so each namespace has a single
  reason to change.
  _Watch for:_ a namespace edited for several unrelated reasons.
- **As concrete as the current need.** Implementation code is written for the
  need in front of it; delete flexibility nothing uses.
  _Watch for:_ abstraction with no seam and no second caller. Framework
  extension points — protocols and multimethods at documented seams — are
  exempt: generality there is the product, judged by whether the seam is real
  (one adapter is hypothetical, two is real).

### Formatting

zprint owns formatting (config in `.zprint.edn`): no commas in maps, forced
newlines in bindings/maps/pairs, hiccup-aware, sorted requires. Do not hand-format
what zprint handles — just run it.

**Always format and lint before committing.** clj-kondo should report no errors;
unused-var warnings are acceptable in some places.

## Commit Conventions

Make small, atomic commits — one thing each, many small commits over one large
one — with conventional commit messages:

- `fix:` bug fixes
- `feat:` new features
- `refactor:` restructuring without behavior change
- `chore:` maintenance
- `test:` adding or updating tests
- `docs:` documentation

A commit message is one line. No body, no bullet list, no trailers. If one line
cannot describe the change, the commit is too big — split it.

Never mention the agent session in a commit: no "Co-Authored-By: Claude", no
"Generated with", no tool or model names.

## Agent skills

### Issue tracker

Issues and PRDs live as GitHub issues in `bolivier/tram`, managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles, each mapped to its identically-named label (`needs-triage`, `needs-info`,
`ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context — one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.

