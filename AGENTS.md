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
- **Keep the starter template in sync.** A framework change that affects
  generated apps must be mirrored in `starter-template/`.

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
| `src/main/tram/generators/`            | Code generators (model, migration)               |
| `src/main/rapid_test/`                 | Test helpers (HTML assertions, hiccup zipper)    |
| `src/cli/tram_cli/`                    | `tram` CLI (generate, daemon, nrepl client)      |
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
clojure -M:clj-kondo                              # lint src + test

# CLI (scaffolding, run from a generated app dir)
./tram generate <thing> ...
```

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
- Predicates end with `?`; mutating functions end with `!`.
- kebab-case for Clojure names, snake_case for database columns.

### Naming Conventions

- Framework namespaces: `tram.*`. Test utilities: `rapid-test.*`. CLI:
  `tram-cli.*`.
- Models use keyword identifiers: `:models/users`, `:models/accounts`.
- Routes use keyword names: `:route/dashboard`, `:route/user`.
- Namespaces with `^:public` metadata are public API.

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

