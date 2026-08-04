# Rhizome build plan

Rhizome's client runtime is being rewritten from scratch with signals in it from
the start. ADR-0005 records that decision and what survives the deletion.

Read `CONTEXT.md` for the vocabulary. Read ADRs 0002 through 0008 for the
decisions the milestones rest on. Nothing below re-argues them.

## The design in one page

An element carries attributes. Two kinds matter.

**Triggers** name an event and hold a directive, a vector of commands run in
order.

```clojure
[:button {::on/click [{:command :http/post :http/url "/save"}]}]
```

**Bindings** derive part of the element from signals. Their value is a quoted
Clojure form, read and interpreted against an allowlist.

```clojure
[:span {::rz/text 'count}]
[:div  {::rz/class {:saving 'in-flight?}}]
```

Signals are the only mutable client state. Commands write them, bindings read
them, and every request carries them to the server as edn. The dom is derived,
never poked at. The exceptions are morph, which the server drives by id, and
`:dom/remove`.

Both commands and bindings are registry entries on a config an application builds
before `start!`. That is how an application adds its own.

## Milestones

One milestone per commit series. Do not start the next until the last runs.

| #  | Milestone                                              | Status      |
|----|--------------------------------------------------------|-------------|
| 0  | Vocabulary and decisions                               | done        |
| 1  | [Mount and the command registry](01-mount-and-registry.md) | drafted |
| 2  | [The wire protocol](02-wire-protocol.md)               | drafted     |
| 3  | [Server sent events](03-server-sent-events.md)         | basic build |
| 4  | The signal store                                       | not written |
| 5  | Expressions                                            | not written |
| 6  | Bindings                                               | not written |
| 7  | Signals on the wire                                    | not written |
| 8  | Navigation                                             | not written |
| 9  | Migration and htmx removal                             | not written |

Milestone 0 is the `### Rhizome` section of `CONTEXT.md` plus ADRs 0003 through
0008. It is written.

Milestones 1 and 2 together replace what the old runtime did, done right. They
ship bodyless requests, which is enough to run the docsite counter. Request
bodies wait for milestone 7, per ADR-0007.

### 3, server sent events

The `text/event-stream` branch that sat commented out in the old `execute-http`.
A stream event names a command and the client runs it through `run!`, so the
milestone adds a branch to the five http commands and no vocabulary. It comes
after milestone 1 because a stream is exactly the thing that must close on
unmount.

The server half is its own document, `../tram/streaming-responses.md`. A handler
returns `:stream` and Tram frames and writes it.

### 4, the signal store

The reactive core, written in cljs. A store of named values, dependency tracking,
and batched updates that stay glitch free when one change reaches a node by two
paths. No dom and no network. This is the piece everything from here on stands
on, so it ships alone and well tested.

### 5, expressions

The reader and interpreter of ADR-0008, plus the default allowlist. **Starts with
a spike:** build the allowlist by hand and with SCI, compile both with
`:advanced`, and compare gzipped size. ADR-0008 leaves the choice open because
nobody has that number.

### 6, bindings

`::rz/text`, `::rz/class`, `::rz/attr`, `::rz/show`, and `::rz/bind`. The second
registry, beside commands, so an application adds its own. `::rz/bind` is the
two-way one, and it is what makes a form field a signal.

### 7, signals on the wire

The request body becomes the signal map as edn. The response gains a signal patch
alongside the element patch. Needs a convention for which signals stay on the
client, an open question in both ADR-0007 and ADR-0008.

### 8, navigation

ADR-0002 names this as the blocker on removing htmx from Tram. Rhizome has no way
to navigate: no redirect command, no `window.location`, no `pushState`.

### 9, migration and htmx removal

The docsite's two examples, the starter template's whole authentication flow, and
then the deletions ADR-0002 authorises: `htmx-request?` and the `hx-redirect` form
of `redirect`.

## What the old runtime got wrong

None of this code survives, so none of these are bugs to fix. They are the
mistakes the rewrite must not make again.

| Mistake                                                                    | Answered by |
|----------------------------------------------------------------------------|-------------|
| A config existed but the code ignored it and called `execute` directly       | ADR-0004    |
| The html attribute string was hardcoded in four places                       | milestone 1 |
| One element could hold one command, so it could not post and set a class     | ADR-0006    |
| Triggers were guessed from tag names, and a `div` fell through to click      | ADR-0006    |
| A closed table of thirty event names rejected everything else                | ADR-0006    |
| `:event/load` was faked as a dom event, with a `TODO: fix this hack`         | ADR-0006    |
| Six ways to name a target, five of them unused                               | ADR-0003    |
| `:on/key` was defined twice, as a filter and as a wrapper                    | milestone 1 |
| `:on/debounce` was registered as a wrapper that did not debounce             | milestone 1 |
| `resolve-body` was called with `nil`, so `:http/body` never reached a request | ADR-0007    |
| `get-body :value` returned the spec vector rather than the value             | ADR-0007    |
| A non-2xx response morphed the error body onto the page                      | milestone 2 |
| Nothing unmounted, so no command could safely hold a resource                | milestone 1 |
| `cljs.core.async` was required and never used                                | ADR-0005    |

## Namespace layout

Not binding. Split only when a namespace has two reasons to change.

```
rhizome/core.cljs             public api: default-config, register, start!, run!
rhizome/mount.cljs            the scan, the observer, listeners
rhizome/signals.cljs          the store and dependency graph
rhizome/expr.cljs             read and interpret, the allowlist
rhizome/commands/http.cljs    the five verbs and response dispatch
rhizome/commands/dom.cljs     morph and remove
rhizome/bindings.cljs         text, class, attr, show, bind
```

Empty jvm namespaces exist so keywords resolve when hiccup is written on the
server: `rhizome/core.clj` today, plus `rhizome/core/on.clj` for `::on/*`.
`rhizome/html.cljc` is the vendored huff fork and is out of scope for all of this.
