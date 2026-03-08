# Tram Framework Review

## 1. API Design & Ergonomics

### What works well for the target audience

- **The Rails-like MVC split** (handlers/views/models + concerns) is immediately recognizable to Rails/Phoenix devs. The directory structure in the starter template maps 1:1 to mental models they already have.
- **`defroutes` with reitit vectors** is clean. The shorthand `:get homepage-handler` expanding to `{:handler homepage-handler}` reduces boilerplate — this is the kind of thing Express/Fastify devs appreciate.
- **Implicit template resolution** (handler ns → view ns) is a strong convention-over-configuration win. A Rails dev will immediately understand "the controller action finds its view."
- **`has-many!`, `has-one!`, `belongs-to!`** — these are literally ActiveRecord method names. A Rails dev sees these and feels at home instantly.
- **The `rapid-test/html` testing library** with `get-by-role`, `get-by-label` is a great call — JS devs coming from Testing Library will recognize the query API immediately.

### Problems that would frustrate the target audience

1. **The interceptor wall in `routes.clj`** is the single biggest onboarding problem. Look at the starter template's router init:

```clojure
:interceptors [(tr/format-interceptor)
               (tr/exception-interceptor)
               authentication-interceptor
               (tr/parameters-interceptor)
               (tr/multipart-interceptor)
               tr/expand-header-routes-interceptor
               tr/format-json-body-interceptors
               (tr/coerce-request-interceptor)
               (tr/coerce-exceptions-interceptor)
               (tr/coerce-response-interceptor)
               (tr/wrap-page-interceptor as-full-page)
               tr/render-template-interceptor]
```

That's **12 interceptors** a new user has to understand, and the **ordering matters** (format before coerce, render last, etc.). An Express dev writes `app.use(express.json())` and moves on. A Rails dev never sees this at all. This should ship as a single `tram-default-interceptors` function that takes a config map, with the ability to customize for advanced users. Something like:

```clojure
(tram-router routes
  {:page-wrapper as-full-page
   :interceptors [authentication-interceptor]}) ; user's custom ones get merged in
```

2. **`tram.core` re-exports almost nothing useful.** It exports `tram-router`, `defroutes`, and config fns — but not `redirect`, `full-redirect`, `htmx-request?`, `make-route`, `make-path`, `early-response`, or any of the interceptors. A new user will `(:require [tram.core :as tram])` and immediately discover they need `tram.routes`, `tram.db`, `tram.html`, etc. separately. Compare Rails where `ApplicationController` gives you everything, or Phoenix where `use MyAppWeb, :controller` imports the whole world. The core ns should be the one-stop shop, or at minimum the docs should be upfront that `tram.routes` is the real main namespace.

3. **The getting-started flow has too many manual steps.** After `tram new`, the user must: install npm deps, start Docker, run `tram dev`, open a REPL file, evaluate `(db/init-migrations)`, evaluate `(db/migrate)`, open another REPL file, evaluate `(go)`. That's ~7 manual steps before seeing "Hello World." Express: `npx create-express-app && npm start`. Rails: `rails new && rails server`. Phoenix: `mix phx.new && mix phx.server`. Consider a `tram dev --setup` that does migrations + server start in one command.

4. **The docs acknowledge a real bug and treat it as normal:** "View namespace changes sometimes don't register. Solution: Add explicit imports in handlers namespace." This is a namespace refresh problem and it's the kind of thing that makes Clojure feel broken to a newcomer. Either fix it (ensure view namespaces are always loaded when handlers require them) or make `defroutes` auto-require the view namespaces at macro-expansion time.

5. **`redirect` returns 301 (permanent) but is the "normal" redirect.** This is wrong by HTTP semantics and will cause caching issues. Most frameworks use 302 or 303 for post-action redirects. `full-redirect` uses 303 which is correct, but the naming implies `redirect` is the standard one. In `src/main/tram/impl/http.clj`, the HTMX redirect should use 200 or 204 with just the HX-Redirect header, not 301.

6. **Dynamic vars (`*current-user*`, `*req*`, `*res*`) are convenient but the binding sites are scattered.** They're bound in `format-interceptor`, `render-template-interceptor`, `expand-header-routes-interceptor` — three different places, not all binding the same set. This means their availability depends on which interceptor has run. A single dedicated interceptor that binds all three, placed early in the chain, would be more predictable.

7. **`defroutes` is a macro but `tram-router` is a function that does almost nothing.** `tram-router` is literally just `(http/router routes options)` — it adds no defaults at all. The starter template passes all the configuration manually. Either `tram-router` should apply Tram's default interceptor stack, coercion, and muuntaja instance, or it shouldn't exist (just tell users to call `reitit.http/router`).

## 2. Idiomatic Clojure

### Good

- Threading macros used well throughout
- Multimethods for handler-spec dispatch is clean
- Zipper-based route walking is elegant
- `import-vars` via potemkin for re-exports is standard practice

### Issues

1. **`*associations*` is a `defonce` atom wrapped in a dynamic var.** This is a confusing hybrid — it's dynamic (suggests thread-local rebinding) but holds an atom (suggests global shared state). Since you never actually rebind it with `binding`, drop the `^:dynamic` metadata. Or if you want test isolation, make it a proper dynamic var holding a plain map and use `binding` in tests. The current pattern looks like an accident.

2. **`has-many!` does a database query at macro/definition time** via `lookup-join-table`. This means requiring a model namespace triggers SQL queries as a side effect of loading code. This will surprise anyone, but especially a JS dev who expects `import` to be side-effect-free. It also means your association definitions depend on a live database connection at load time, which breaks REPL-first workflows when the DB is down. Consider making join-table detection lazy (first hydration call) or explicit.

3. **The `coerce-route-entries-to-specs` function uses `*ns*` at macro expansion time** to derive handler/view namespaces. This is correct for `defroutes` (which captures `*ns*` at compile time) but it's a subtle Clojure-ism that would be very confusing to debug if something goes wrong. The namespace derivation logic (handler → view) is also hidden — a user seeing their template not resolve has no clear path to understanding why. Consider an explicit `:views-ns` option on routes as an escape hatch.

4. **The `simple-hydrate` multimethod dispatch on `[:default :default]`** is a catch-all that handles has-many, has-one, and a fallback case. This means Tram hijacks Toucan2's hydration for ALL models, not just ones with declared associations. The fallback at line 177 of `associations.clj` tries to pluralize and select — this will produce confusing SQL errors for attributes that aren't associations. The `:else` branch should throw an informative error or check the registry first.

5. **Inconsistent model keyword convention.** The docstring in `associations.clj` says `:model/users` (singular namespace) but the actual code and docs use `:models/users` (plural namespace). The `has-one!` docstring at line 62 says `(has-one! :model/users :address)` (singular). Pick one and be consistent — this will trip up every new user.

6. **`default-error-handler` returns status 504.** 504 is Gateway Timeout — the correct status for an unhandled exception is 500 Internal Server Error. A JS dev will see 504 in their browser devtools and think there's a proxy/timeout issue.

## 3. Performance Considerations

1. **`prewalk` over all response headers on every request** in `expand-header-routes-interceptor` is unnecessary overhead for the common case where headers contain no route references. Most responses have 3-5 headers with plain string values. Consider checking if expansion is actually needed first, or only expanding specific headers that are known to contain routes (like `Location`).

2. **`transform-keys` on every JSON request/response body** in `format-json-body-interceptors` walks the entire data structure twice (once in, once out). For large JSON payloads this is O(n) with significant constant factors. This is fine for typical web app responses but worth documenting. A Node.js dev used to `JSON.parse` being native C++ code might notice the difference on large payloads.

3. **No type hints anywhere.** For a framework that will be in the hot path of every request, the core interceptor functions should have type hints on string operations to avoid reflection. Specifically, `str/starts-with?` calls in `wrap-page-interceptor` and `render-template-interceptor` will reflect on every request unless the URI is hinted as `^String`. Run `lein check` or `(set! *warn-on-reflection* true)` and fix the warnings in the interceptor chain.

4. **`lookup-join-table` in associations does a `SELECT` query and catches the exception to detect if a table exists.** This is both slow (exception-based control flow) and fragile (regex on error messages across DB engines). Use information_schema or a DB-specific table existence check instead.

5. **The muuntaja instance is created per-route at compile time** (`:compile` fn in `format-interceptor`), which is correct and efficient. This is good — no per-request overhead.

6. **http-kit is a solid choice** — competitive with Netty-based servers for most workloads. A Node.js dev will find the throughput story credible. Worth mentioning in docs since "can it handle the load?" is a top concern for anyone leaving Node.

## 4. Comparison to Existing Frameworks

**vs. Ring/Reitit (raw Clojure stack):**
Tram's value add is clear: convention-over-configuration for template resolution, associations, and the handler/view split. A Reitit user has to wire all this up themselves. The interceptor stack is where Tram should shine most — but currently it doesn't because the user still assembles it manually. **Fix the `tram-router` function to include sensible defaults and this becomes the single strongest selling point.**

**vs. Pedestal:**
Pedestal is more enterprise-oriented and verbose. Tram is simpler and more approachable. Good positioning.

**vs. Rails:**
The honest gap: Rails gives you generators, migrations, a CLI that actually does things (`rails generate scaffold`), asset pipeline, mailer, jobs, and ActiveRecord out of the box with zero configuration. Tram's Claude Code commands (`/tram-scaffold`, `/tram-model`) are creative and interesting — AI-assisted scaffolding is genuinely novel — but they're not a substitute for a real CLI generator that works without AI. `tram new` exists, but `tram generate model` and `tram generate scaffold` should too, independent of Claude.

**vs. Phoenix:**
Phoenix's key differentiator is LiveView for real-time UIs without JavaScript. Tram's HTMX story is the right counter — it's simpler and doesn't require learning a framework-specific abstraction. But Phoenix also has incredible error messages, `mix phx.routes` to see all routes, and automatic code reloading that just works. Tram's "views sometimes don't register" issue is the anti-Phoenix experience.

**vs. Express/Next.js:**
The honest pitch to a JS dev: "You get a real type system (via Malli), a real REPL, macros, and the JVM's performance and library ecosystem — with Rails-like conventions so you're not assembling middleware from scratch." The weakness: the Clojure ecosystem has far fewer libraries, the error messages are worse, and the tooling (editors, debuggers) is less polished. Tram can't fix those, but it can minimize exposure to them.

**The niche:** Tram is positioning itself as "Rails for Clojure" which is a real gap. Biff exists in this space but is more opinionated (Xtdb, htmx, specific auth). Tram's choice of Toucan2 + Reitit + Integrant is more mainstream Clojure. The "removable batteries" pitch is smart — it's the right message for Clojure devs who are allergic to frameworks but secretly want one.

## Top 5 Priorities

1. **Ship a `tram-defaults` interceptor stack** that `tram-router` applies automatically. Let users add/remove/reorder for advanced cases, but the starter template should be ~5 lines, not 30.

2. **Fix `tram.core` to be the actual entry point.** Re-export `redirect`, `full-redirect`, `make-path`, `make-route`, `early-response`, the interceptor constructors, and the DB operations. A user should be able to `(:require [tram.core :as tram])` and get 90% of what they need.

3. **Fix the 301 redirect** to 303 or 302. Fix the 504 error status to 500. These are small but signal "the author doesn't know HTTP" to experienced web devs, which undermines trust.

4. **Make `tram dev` start the server and run migrations automatically** (or offer a `--full` flag). Reduce the "first request" path to 2 steps: `tram new` → `tram dev` → see a page.

5. **Add `*warn-on-reflection* true`** to the framework's core namespaces and fix the warnings. This is table-stakes for a framework that sits in the request hot path and it's free performance.
