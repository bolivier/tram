# CLAUDE.md

## Project Overview

Tram is an opinionated Clojure web framework (early alpha) for rapid application
development. It wraps Toucan2 (ORM), Reitit (routing), Selmer (templates), and
Integrant (dependency injection) into a Rails-inspired stack.

## Version Control

Use `jj` (Jujutsu) instead of `git` for all version control operations.

Make small, atomic commits with conventional commit messages:
- `fix:` for bug fixes
- `feat:` for new features
- `refactor:` for restructuring without behavior change
- `chore:` for maintenance tasks
- `test:` for adding or updating tests
- `docs:` for documentation changes

Each commit should do one thing. Prefer many small commits over one large
commit.

If work related to a previous commit comes up later, use jj to jump back and
edit the original changeset with `jj edit <changeid>`

## Development Workflow

### Test-Driven Development

Always write tests first, then implementation. The cycle is:
1. Write a failing test
2. Write the minimal code to make it pass
3. Refactor
4. Commit at each meaningful step

### Running Tests

```sh
clojure -M:test          # run all tests (Kaocha)
clojure -M:test --watch  # watch mode
bin/test-bb              # babashka tests
```

### Formatting & Linting

```sh
zprint '{:search-config? true}' -w src/**/*.clj   # format files in-place
zprint '{:search-config? true}' -c src/**/*.clj    # check formatting
clojure -M:clj-kondo                               # lint
```

Formatting is enforced by zprint (config in `.zprint.edn`). Linting uses
clj-kondo (config in `.clj-kondo/config.edn`). Always format and lint before
committing.

### Clojure MCP Server

Use the Clojure MCP server (nREPL) for evaluating code, running tests
interactively, and inspecting state. If no nREPL server is running, prompt the
user to start one before proceeding with REPL-dependent work.

## Code Style

### General Clojure Idioms

- Use idiomatic Clojure: prefer `map`/`filter`/`reduce` over manual recursion
- Use threading macros (`->`, `->>`) for data transformation pipelines
- Prefer pure functions; isolate side effects at the edges
- Use destructuring in function arguments and `let` bindings, but only one level
- Predicate functions end with `?`, mutation functions end with `!`
- kebab-case for Clojure names, snake_case for database columns

### Namespace Conventions

- Main framework code: `tram.*`
- Testing utilities: `rapid-test.*`
- Models use keyword identifiers: `:models/users`, `:models/accounts`
- Routes use keyword names: `:route/dashboard`, `:route/user`
- Namespaces with `^:public` metadata are public API

### Formatting Rules

Zprint handles formatting. Key conventions already configured:
- No commas in maps
- Force newlines in bindings, maps, and pairs
- Hiccup-aware formatting
- Sorted requires

Do not manually reformat code that zprint handles. Just run the formatter.

## Starter Template should be kept up-to-date

If there are changes that affect the starter template, make those changes in the
starter template with the feature changes.

## Database

- Tests use SQLite (`resources/test.db`) -- no external DB needed
- Development/production uses PostgreSQL or SQLite
- Toucan2 is the ORM; prefer its API (`select`, `insert!`, `delete!`, `update!`) over raw SQL
- Migrations are managed by Migratus (test migrations in `resources/test-migrations/`)
- Associations: `has-many!`, `has-one!`, `belongs-to!` macros in `tram.associations`

## Key Paths

- `src/main/tram/core.clj` -- main public API (re-exports via `import-vars`)
- `src/main/tram/db.clj` -- database operations
- `src/main/tram/routes.clj` -- routing macros and interceptors
- `src/main/tram/html.clj` -- HTML utilities and path generation
- `src/main/tram/associations.clj` -- ORM associations
- `test/main/tram/test_fixtures.clj` -- test setup, fixtures, sample data
- `deps.edn` -- dependencies and aliases
- `tests.edn` -- Kaocha test runner config
- `tram.edn` -- framework config (DB connection, etc.)

## Dependencies (key ones)

- **toucan2** -- ORM (local dep at `../toucan2`)
- **reitit** -- routing
- **selmer** -- templates
- **integrant** -- dependency injection
- **malli** -- schema validation
- **kaocha** -- test runner
- **matcher-combinators** -- test assertions
- **huff2** -- hiccup/HTML processing
