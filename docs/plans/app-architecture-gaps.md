# App architecture gaps

An evaluation of the architecture a Tram app gets today, and what it lacks. Read
against `starter-template/`, the docsite app, and `CONTEXT.md`.

## What a Tram app has today

| Layer       | Namespace              | Job                                    |
| ----------- | ---------------------- | -------------------------------------- |
| System      | `config.clj`           | Integrant map: server, app, router, routes |
| Pipeline    | `routes.clj`           | interceptor chain assembly             |
| Handler     | `handlers/*`           | request → response                     |
| View        | `views/*`, `components/*` | locals → hiccup                     |
| Model       | `models/*`             | Toucan2 hooks, Malli schemas           |
| Persistence | `db.clj`               | connection, key transforms             |
| Misc        | `concerns/*`           | everything else                        |

That covers the request, the database, and the page. It covers nothing else.

## Gap 1: no layer between the handler and the model

`concerns/` is the junk drawer, and the starter app proves it.
`starter-template/src/sample_app/concerns/authentication.clj` holds password
hashing, a raw user query, account registration with SQLite result-code
introspection, cookie string parsing, and an interceptor. That is five reasons
to change in one namespace.

`CLAUDE.md` says a namespace changes for one reason. The starter breaks that
rule because there is nowhere else to put the code.

Two more problems fall out of this. `concerns/http.clj` is a page fn, so
`concerns/` also means rendering. And `CONTEXT.md` already defines
**concern-group** as a pipeline term for interceptor clusters. The app directory
and the framework vocabulary now use one word for two things.

## Gap 2: no seam for anything outside the process

Nothing in the framework or the template shows how to add a component to the
system. `config.clj` is a `defonce system` with four keys and no example of a
fifth.

A handler also has no way to reach one. Handlers get a request map, and the
request carries only what `tram.routes` and app interceptors put there.
`tram.vars` offers `*req*`, `*res*`, and `*current-user*`, and nothing else.

Watch what happens without that seam. The docsite handler declares
`(def global-count (atom 420))` at namespace top level. That is the missing
component registry showing up as a global var. The database works only because
`db.clj` installs a `t2/do-with-connection :default` method, a hardcoded global
of a different shape. Redis and S3 get no such trick.

## The obvious protocol: file storage

Both apps ship `models/file.clj`, a Malli schema for a multipart upload. Neither
app does anything with it. Multipart parsing already happens in the wire-format
group, so uploads arrive and then dead-end.

This is the protocol to write first:

```clojure
(defprotocol Storage
  (put! [this key stream opts])
  (fetch [this key])
  (url-for [this key opts])
  (delete! [this key]))
```

Disk for dev and test, S3 for production. Two implementations, so the seam is
real by the rule in `CLAUDE.md`. Tram already has the pattern in
`tram.sse/StreamSource`, where the framework accepts a channel or a seq and the
app adds its own. Storage is the same shape with a far more common need behind
it.

## What else is missing

- **Mail.** The template ships sign-up and sign-in. Password reset and email
  confirmation are impossible today.
- **Background jobs.** With no queue, sending that mail blocks the request
  thread.
- **Cache.** No Redis, no memoization seam, no cache key convention.
- **Outbound HTTP.** No convention for calling a third-party API, and no way to
  fake one in a test.
- **Config and secrets.** `tram.edn` holds databases and a project name. The
  template reads `CSRF_SECRET` with a bare `System/getenv` in `config.clj`. Aero
  is already the reader, so `#env` and `#profile` work, but nothing says so and
  there is no app section. Every service added will re-invent secret reading.
- **Test doubles.** Tests hit real SQLite, which works. An app with S3 needs a
  fake, and without a protocol there is no fake to write. `rapid-test.*` covers
  HTML only.
- **Error taxonomy.** `tram.errors` has one function. Once services exist, an S3
  timeout needs a defined shape for the exception interceptor to map.

## What to build

1. **A component seam.** Framework Integrant keys, plus one interceptor that
   puts the running components on the request. Document adding a key to
   `config.clj` as the normal way to add a service. Bind a
   `tram.vars/*components*` var alongside `*req*` if views and services should
   skip threading it.
2. **Framework protocols with a dev implementation included.**
   `tram.storage/Storage`, `tram.mail/Mailer`, `tram.cache/Cache`,
   `tram.queue/Queue`. Ship disk, log, atom, and in-thread versions so a new app
   runs with zero external services. Production swaps the component.
3. **Split `concerns/`.** Move interceptors to `interceptors/`. Move the page fn
   next to views. Give the leftover domain logic a real home, `services/`: code
   that talks to something outside the process, plus the operations that compose
   a model and a service. That name also frees `concern` to mean only what
   `CONTEXT.md` says it means.

Storage is the right first cut. It has a schema already written, an input path
already parsed, and two implementations that are both genuinely needed.
