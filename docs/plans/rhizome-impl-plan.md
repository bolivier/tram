# Rhizome implementation plan

This file turns the rhizome build plan into ordered, committable work. The specs
in `docs/specs/rhizome/` own behaviour. ADRs 0002 through 0008 own the
decisions. This file owns sequencing, file-level tasks, test strategy, exit
criteria, and the decisions still to make. Nothing here re-argues an ADR.

Each milestone is one commit series. Within a milestone, follow the loop:

1. Write or finish the spec.
2. Implement against it.
3. Prove the exit criteria.
4. Update the status table in `docs/specs/rhizome/README.md`.

## Departure from the README milestone order

This plan moves server sent events from milestone 3 to last. Reasons:

- Nothing on the path to htmx removal needs a stream. The docsite, the starter
  template, and navigation all run on plain requests.
- The README placed it early because "a stream is exactly the thing that must
  close on unmount". Milestone 2 already proves that seam: an in-flight request
  aborts on unmount through the same `:on-trigger` / `:on-unmount` pair.
- Half of SSE's value is the `application/edn` branch, which patches signals.
  Signal patches do not exist until signals reach the wire.

Update the README milestone table when this plan lands. The renumbering below is
the plan's own; specs keep their file names.

| Phase | Work                           | Old milestone # |
|-------|--------------------------------|-----------------|
| 0     | Delete, scaffold, test infra   | part of 1       |
| 1     | Mount and the command registry | 1               |
| 2     | The wire protocol              | 2               |
| 3     | The signal store               | 4               |
| 4     | Expressions                    | 5               |
| 5     | Bindings                       | 6               |
| 6     | Signals on the wire            | 7               |
| 7     | Navigation                     | 8               |
| 8     | Migration and htmx removal     | 9               |
| 9     | Server sent events             | 3               |

## Phase 0: delete, scaffold, test infra

The rewrite starts with the deletion ADR-0005 authorises, plus the
infrastructure every later phase leans on. The repo has no cljs test story
today, and the signal store cannot ship "alone and well tested" without one.

Tasks:

- Delete `src/main/rhizome/core.cljs` and `src/main/rhizome/command.cljs`. The
  docsite build breaks here and stays broken until phase 2. ADR-0005 accepts
  that.
- Drop the cljs deps the old runtime used: `cljs-http`, `promesa`, and the
  unused `core.async` require. The new runtime uses `js/fetch` and
  `AbortController`. Smaller bundle, fewer deps.
- Create the namespace skeleton from the README layout: `rhizome/core.cljs`,
  `rhizome/mount.cljs`, plus empty files for later phases as they arrive. Add
  `rhizome/core/on.clj`, the empty jvm namespace that makes `::on/*` keywords
  resolve in server-side hiccup.
- Port the browser test harness from the old rhizome repo at `~/code/rhizome`.
  It is proven and covers async tests, which shadow's own browser runner
  cannot report on. The pieces:
  - A `:wtr` shadow build, `:target :esm`, exporting `init` and `start`.
  - The `rhizome.wtr` adapter, which resolves a promise from cljs.test's
    `:end-run-tests` summary and feeds `sessionFinished` on @web/test-runner.
  - `test-runner-index.html`, with the skip-completing flag that keeps the
    browser open across hot reloads in manual mode.
  - The msw fake server with ring-shaped handlers, so http commands run
    against real `fetch` through a service worker.
  - Test utils: `with-html`, the `eventually` assert-expr, `fire!`,
    `fire-key!`, `type!`, and sinon fake timers scoped to `setTimeout`.
- The harness needs npm in the framework repo: `@web/test-runner`,
  `@web/test-runner-chrome`, `msw`, `sinon`, `shadow-cljs`. Add
  `package.json`; run headless in CI, headed in manual mode.
- One harness for all cljs tests. Pure namespaces, signals and expressions,
  run in it too. A `:node-test` build is a later option if browser startup
  becomes the slow part.
- Add `bin/test-cljs` to invoke it. Per the repo rule, the invocation lives
  in a script, not in alias `:main-opts`.

The old repo also holds prior art to read, not port: `signals.cljs` there
couples the store to dom elements, the shape ADR-0007 rejects, and
`event_stream.cljs` is a phase 9 reference.

Exit criteria: `bin/test-cljs` runs an empty suite green in headless chrome.

## Phase 1: mount and the command registry

Spec: `01-mount-and-registry.md`, already drafted. Implements ADR-0004 and
ADR-0006.

Client tasks:

- `rhizome/core.cljs`: `default-config`, `register`, `register-trigger`,
  `start!`, `run!`. `start!` stores the config; a second call reports through
  `:on-error`.
- `rhizome/mount.cljs`: the selector built from `:triggers`, the scan, edn
  parse, schema check at mount, listener attachment, the mount record on the
  element, and unmount. One `MutationObserver` on `document.body`.
- Modifiers: `:key`, `:prevent`, `:stop`, `:once`, `:debounce`, `:throttle`.
  One test per modifier. The old runtime shipped a debounce that did not
  debounce; the test suite is the guard against a repeat.
- The development-mode walk that reports `rhizome_core_on___*` attributes the
  config does not know. Once per `start!`, through `:on-error`.
- Change the shadow-cljs `:init-fn` target to the new entry point. The drop-in
  behaviour is `(rz/start! (rz/default-config))` on `DOMContentLoaded`.

Server tasks:

- `emit-attr` in `rhizome/html.cljc` dispatches on the full attribute keyword.
  That cannot cover an open trigger set; nobody can `defmethod` every event
  name. Change the dispatch fn to return one dispatch value for any keyword in
  the `rhizome.core.on` namespace. The existing `:rhizome.core/on` method in
  `tram.html` becomes that method. Keep its route-name expansion and its
  `pr-str` serialization.
- Spell every attribute name through `rhizome.html/kw->string`. The old runtime
  hardcoded the string in four places.
- Hiccup helpers: one per http verb defaulting to `::on/click`, plus
  `rz/submit` defaulting to `::on/submit`. Second arity takes the trigger.
  These are pure fns over data; they can trail the runtime inside the phase.

Tests: registry fns under node. Mount, observer, modifiers, and the dev-mode
walk under the browser build. The `emit-attr` change and helpers under the
existing jvm suite.

Exit criteria: an element with a registered no-op command mounts, fires on its
trigger, and unmounts when removed. An app-registered command works through
`register`.

Open questions to settle in the spec before code:

- Does `:once` unmount or stop listening? (spec 01)
- Does a schema failure block the element or the command? (spec 01)

## Phase 2: the wire protocol

Spec: `02-wire-protocol.md`, already drafted. Implements ADR-0002, ADR-0003,
and the morph rule.

Client tasks:

- `rhizome/commands/http.cljs`: the five verbs on `js/fetch`. Bodyless. Every
  request carries `rhizome-request: true`. `:on-trigger` returns the abort
  controller; `:on-unmount` aborts it.
- Response dispatch on content type. `text/html` morphs. Unknown types and
  non-2xx go to `:on-error`. A 204 morphs nothing. A 3xx goes to `:on-error`
  until navigation exists.
- `rhizome/commands/dom.cljs`: `:dom/morph` over idiomorph with `outerHTML`,
  matching by id from the directive element's root node. Per-element failure
  reporting; one bad element does not stop the rest. `:dom/remove` with
  optional `:dom/id`.
- The http commands reach morph through `run!`, not by direct call.

Server tasks:

- Add a development-mode check in the render concern-group: when
  `rhizome-request?` is true, every top-level element in the response carries
  an id. ADR-0003 makes this a hard requirement, and today nothing enforces
  it. Log or throw in dev; decide which in the spec.

Migration tasks:

- Rewrite `docsite/src/tram_docs/views/examples_views.clj`: both counters and
  the edit-row example, from `::rz/on` with `:op` and `:ident` to `::on/*`
  with `:command`. Every partial root gets an id.
- Rewrite `rhizome-command-attribute-survives-the-browser-test` in
  `test/main/tram/html_test.clj` off `:ident [:closest ...]`.

Exit criteria: the docsite builds and the counter examples work end to end in
a browser. This phase closes the "old runtime replaced, done right" line and
the docsite stops being broken.

Open question to settle in the spec: are concurrent requests from one element
cancelled, queued, or ignored? Datastar cancels by default.

## Phase 3: the signal store

Spec to write: `03-signal-store.md` (renamed from the README's slot 4).
Implements the store half of ADR-0007.

- `rhizome/signals.cljs`: named values, subscription, dependency tracking, and
  batched glitch-free updates. No dom, no network. Pure cljs under
  `:node-test`.
- Property-style tests on the diamond case: one change reaching a node by two
  paths applies once.
- Register `:signal/set` as a command. It writes the store and nothing else.
  It has no visible effect until bindings, which is fine.

Exit criteria: store test suite green under node; `:signal/set` updates a
value a subscriber observes exactly once per batch.

## Phase 4: expressions

Spec to write: `04-expressions.md`. Implements ADR-0008.

- **Spike first, code second.** Build the allowlist interpreter by hand and
  with SCI. Compile both with `:advanced`. Compare gzipped size against the
  reference points: htmx ~14kb, Datastar ~11kb. Record the choice and the
  numbers as ADR-0009. ADR-0008 leaves this open on purpose.
- `rhizome/expr.cljs`: read the attribute text, interpret against the
  allowlist, `and` / `or` / `if` as special forms. A bare symbol reads a
  signal. Unknown symbol or bad arity errors at mount.
- Static dependency extraction: walk the form, collect symbols in operand
  position. Bindings use this to subscribe.
- Enumerate the default allowlist in the spec. Start small: `not`, `=`,
  `not=`, `<`, `>`, `count`, `str`, `empty?`, `seq`. Grow on demand.

Exit criteria: interpreter suite green under node. ADR-0009 committed with
the measured numbers.

## Phase 5: bindings

Spec to write: `05-bindings.md`. Implements the binding half of ADR-0007.

- `rhizome/bindings.cljs`: `::rz/text`, `::rz/class`, `::rz/attr`,
  `::rz/show`, `::rz/bind`. The second registry on the config. The mount
  selector extends with the binding attribute names.
- `::rz/bind` is two-way: input events write the signal, signal changes write
  the control.
- Server side: `emit-attr` for `rhizome.core/*` binding attributes emits the
  quoted form with `pr-str`.
- The spec must answer one question the ADRs leave implicit: what happens when
  a morph replaces an element that carries bindings. The morphed html is
  server-authored; the binding re-applies from signal state. Which wins, and
  when, needs a written rule before this ships.

Exit criteria: the docsite gains a client-only example, a counter or a
show/hide, that round-trips no request.

## Phase 6: signals on the wire

Spec to write: `06-signals-on-the-wire.md`. Completes ADR-0007.

- Before code, write the ADR on client-private signals. ADR-0007 and ADR-0008
  both raise it; both want one answer. Datastar uses a leading underscore.
  Decide deliberately.
- Request body becomes the signal map as edn, minus private signals.
- Response dispatch gains `application/edn`: a signal patch applied to the
  store.
- Server side: a helper to read the signal map from a rhizome request. Tram
  speaks edn end to end already; check the wire-format group parses it without
  new work.
- Update the dev-mode partial check if signal-only responses change what a
  partial may contain.

Exit criteria: the edit-row docsite example works with no form scraping. A
bound field reaches the server as a signal.

## Phase 7: navigation

Spec to write: `07-navigation.md`. Closes the gap ADR-0002 names as the
blocker on htmx removal.

- A navigation command, provisionally `:nav/redirect`, driven by the server
  response. Covers the `hx-redirect` use the starter template's auth flow
  depends on.
- Decide the 3xx story: what the client does with a redirect status on a
  partial request, and what the server should send instead.
- Decide the failed-request rendering question from spec 02 here, as an ADR.
  A 422 carrying validation errors is an ordinary outcome the page must show.
  The likely shape: 4xx with `text/html` morphs normally, 5xx goes to
  `:on-error`. The starter template migration depends on this answer.
- History (`pushState`) only if the starter template needs it. Otherwise defer.

Exit criteria: a partial response can send the browser to a new url.

## Phase 8: migration and htmx removal

Spec to write: `08-migration.md`. Executes the deletions ADR-0002 authorises.

- **Distribution first.** A generated app needs a compiled `rhizome.js` and
  has no cljs build. The old repo already solved this with a `:dist` shadow
  build: a single self-initializing script released into `resources/`, which
  sits on the consumer's classpath because tram is a dep. The app serves it
  with no JS tooling of its own. Port that build; add a `bin/` script for the
  release step and check the artifact freshness in CI.
- Starter template: drop the unpkg `htmx.org` and `htmx-ext-response-targets`
  scripts, add rhizome.js, migrate the auth flow off `redirect` /
  `hx-redirect` onto navigation, and migrate its forms to signals.
- Delete `htmx-request?` and the `hx-redirect` form of `redirect`. Remove, not
  deprecate.
- Sweep the rest: `starter-template/.claude/commands/tram-htmx.md`, the htmx
  references in `concerns/http.clj` on both apps, and any test that reads the
  htmx header.
- Collapse the partial test to the one rhizome check in `uses-layout?` and the
  page-wrapping path, per ADR-0002.

Exit criteria: `grep -ri htmx` over `src`, `starter-template`, and `docsite`
finds nothing. A scaffolded app authenticates through rhizome.

## Phase 9: server sent events

Spec to write: `09-server-sent-events.md`, adapted from the README's slot 3.

- `text/event-stream` on the response dispatch. Events carry either branch:
  html to morph or edn to patch signals.
- The stream is mount state: `:on-trigger` returns it, `:on-unmount` closes
  it.
- Reconnection policy, and the retry question spec 02 deferred.

Exit criteria: a docsite example streams updates into a morphing element and
closes cleanly on unmount.

## ADRs this plan schedules

| ADR  | Question                                   | Phase |
|------|--------------------------------------------|-------|
| 0009 | Interpreter: hand-written or SCI, with numbers | 4 |
| 0010 | Client-private signal convention           | 6     |
| 0011 | Failed requests: what renders, what errors | 7     |

Numbers are provisional; take the next free slot at writing time.

## Standing rules for every phase

- Specs 03 through 09 do not exist yet. Writing the spec is the first commit
  of its phase.
- Small conventional commits; format with zprint and lint before each.
- A framework change that touches generated apps lands in `starter-template/`
  in the same phase.
- Update the README status table as each phase completes.
