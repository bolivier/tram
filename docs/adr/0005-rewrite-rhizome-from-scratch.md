# Rewrite the rhizome client runtime from scratch

## Status

accepted

## Decision

Delete `src/main/rhizome/core.cljs` and `src/main/rhizome/command.cljs` and build
the client runtime again, with signals in it from the start.

What survives:

- `src/main/rhizome/html.cljc`, the vendored huff fork. It renders hiccup on the
  server and has nothing to do with the client runtime.
- `src/main/rhizome/core.clj` and `html_core.clj`, the empty namespaces that exist
  so `::rz/*` keywords resolve on the jvm. A new `rhizome/core/on.clj` joins them,
  for the `::on/*` trigger keywords ADR-0006 introduces.
- `vendor/idiomorph.esm.js`. Morph is still the right algorithm.
- The server side: `tram.html`'s `emit-attr` methods, `tram.impl.http`'s
  `rhizome-request?`, and the render concern-group. Attribute names change, the
  mechanism does not.

## Considered options

- **Refactor in place, one spec at a time.** The original plan, six commits from
  removing `:ident` to adding signals. Rejected once signals became the goal
  rather than an addition. The existing design puts one imperative command on one
  element and reaches the dom directly. A signal runtime derives the dom from
  state. Almost every part is touched: the attribute scheme changes, the dispatch
  changes, the dom commands are deleted, the request body changes. The refactor
  would spend six commits arriving at a file with none of its original lines.
- **Build the signal runtime beside the current one and switch over.** Both alive,
  migrate the docsite, then delete. Rejected: rhizome has two consumers, the
  docsite and nothing else. The starter template still ships htmx. There is no
  installed base to protect, so the compatibility period buys nothing and costs
  two runtimes on the page.

## Consequences

- **Tram is early alpha and this is the moment to do it.** ADR-0002 already
  established that rhizome owes no compatibility. The cost of this rewrite only
  goes up.
- **The docsite breaks until the rewrite catches up.** `examples_views.clj` is the
  only consumer, with seven directives across two examples. It gets rewritten with
  the runtime, not after it.
- **The known-defect list from the original plan stops mattering.** The body
  pipeline bugs, the stubbed debounce, the twice-defined `:on/key`, the hardcoded
  attribute strings: none of that code survives. The list is kept in the build plan
  only as a record of what the new design must not reproduce.
- The removal of htmx from Tram, which ADR-0002 blocks on rhizome gaining
  navigation, is unaffected. It still waits on the navigation milestone.
