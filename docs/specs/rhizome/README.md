# Rhizome rework

`src/main/rhizome/core.cljs` grew by accretion. It carries a targeting language
nobody wants, a command dispatch that ignores its own config, a stub debounce, and
a dead async require. This directory holds the plan to rebuild it, one spec at a
time.

Read `CONTEXT.md` for the vocabulary. Read `docs/adr/0002`, `0003`, and `0004` for
the decisions these specs rest on.

## Vocabulary, in one line

A **directive** is the edn map in an element's `::rz/do` attribute. It names one
**command** and carries that command's arguments and its **trigger**. Rhizome
resolves the command in a **registry**, which lives on the **config** an
application passes to `start!`. Rhizome **mounts** a directive onto its element,
and **unmounts** it when the element leaves.

## Order

Each spec lands on its own commit. Do not start the next one until the last is
implemented and its migration is done.

| #  | Spec                                                 | Status              |
|----|------------------------------------------------------|---------------------|
| 00 | Vocabulary and decisions                             | done                |
| 01 | [Morph replaces by id](01-morph.md)                  | drafted             |
| 02 | [The command registry](02-command-registry.md)       | drafted             |
| 03 | Triggers and modifiers                               | not written         |
| 04 | Request commands                                     | not written         |
| 05 | Lifecycle and unmount                                | not written         |
| 06 | Server sent events                                   | not written         |
| 07 | Navigation                                           | not written         |

Spec 00 is the `### Rhizome` section of `CONTEXT.md`, plus ADR-0003 and ADR-0004.
It is already written.

### 01, morph replaces by id

The smallest cut and the largest one. Morph matches a fragment element to the
page element with the same id, and nothing else selects a target. Takes `:ident`,
`get-target`, `precedes?`, and `follows?` out with it. See ADR-0003.

### 02, the command registry

Replaces the `execute` multimethod with a registry of command definitions, each a
map of `:key` and lifecycle fns. Introduces the config, `register`, and `start!`.
Renames the attribute from `::rz/on` to `::rz/do` and derives its html spelling in
one place instead of four. See ADR-0004.

### 03, triggers and modifiers

How a directive picks its dom event, and what modifies a trigger before it runs
the command. Resolves `:on/key` and `:on/debounce`, which are currently defined
twice and stubbed respectively. Decides whether a modifier filters, wraps, or
both, and how an application adds one.

Depends on 02, which owns the config key modifiers live under.

### 04, request commands

The five http commands as registry entries. Fixes the body pipeline, which is
broken in two places today. Decides what a rhizome request sends, what content
types it accepts, and what happens on a non-2xx response. Today a failed request
logs to the console and the page does not change.

Depends on 01 for the morph it hands its response to.

### 05, lifecycle and unmount

When rhizome mounts a directive, when it unmounts one, and what a command may
hold between the two. Covers the mount state a command returns from `:on-mount`,
detection of a removed element, and the rewiring idiomorph triggers on the nodes
it adds. Today nothing unmounts, so anything holding a connection or an interval
leaks.

Depends on 02 for the lifecycle fns and on 01 for the morph that removes nodes.

### 06, server sent events

The `text/event-stream` branch that sits commented out in `execute-http` today.
Covers the wire format, how a `rhizome-patch-element` event reaches morph, and
reconnection. Cannot be specified before 05, because a stream is exactly the
thing that must close on unmount.

Depends on 05.

### 07, navigation

ADR-0002 names this as the blocker on removing htmx from the framework. Rhizome
has no way to navigate: no redirect command, no `window.location`, no
`pushState`. The starter template's whole authentication flow depends on the
`hx-redirect` header, so htmx cannot go until this exists.

Depends on 02.

## Known defects

Found while reading the current file. Each is assigned to the spec that owns it.
None are fixed yet.

| Defect                                                                                     | Owner |
|--------------------------------------------------------------------------------------------|-------|
| `execute-http` calls `(resolve-body el nil)`, so a directive's `:http/body` never reaches the request | 04    |
| `get-body :value` returns the spec vector `[:value x]` rather than `x`                       | 04    |
| A non-2xx response is indistinguishable from a 200 and morphs the error body onto the page   | 04    |
| `wire-el!` takes a config but calls `execute` directly, ignoring the config's `:op/handler`   | 02    |
| The html attribute string `"rhizome_core___on"` is hardcoded in four places                  | 02    |
| `cljs.core.async` is required and never used                                                 | 02    |
| The `"load"` event type is special-cased with a `TODO: fix this hack` comment                | 02    |
| `:on/key` is defined twice, once in `:op/filters` and once in `:op/wrappers`                 | 03    |
| `:on/debounce` is registered as a wrapper that does not debounce                             | 03    |
| Nothing unmounts a directive, so a command cannot safely hold a resource                     | 05    |

## Namespace layout

Not binding. Decide at implementation, and only split when a namespace has two
reasons to change.

```
rhizome/core.cljs           public api: default-config, register, start!, run!
rhizome/mount.cljs          reading directives, resolving triggers, listeners
rhizome/commands/dom.cljs   :dom/morph and the attribute commands
rhizome/commands/http.cljs  the five http commands
```

`rhizome/html.cljc` is a vendored huff fork and is out of scope for all of this.
