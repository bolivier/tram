# Expressions are Clojure forms read against an allowlist

## Status

accepted. The interpreter's implementation is open, see Open questions.

## Decision

A binding's value is a quoted Clojure form. The server serializes it with
`pr-str`, so the attribute holds Clojure source text. The client reads it back and
interprets it against an allowlist of functions.

```clojure
[:span {::rz/text 'count}]
[:div  {::rz/show '(not editing?)}]
[:div  {::rz/class {:active '(= status "active")}}]
```

```html
<div rhizome_core___show="(not editing?)">
```

**A bare symbol is a signal.** `count` reads the `count` signal. **The head of a
list is a function**, resolved in the allowlist. So `count` alone is a signal and
`(count items)` calls the allowlisted `count` on the `items` signal. Standard Lisp
evaluation positions disambiguate this, and no sigil is needed.

**The allowlist is config.** Rhizome ships a default set and an application adds
to it. `and`, `or`, and `if` are interpreter special forms rather than allowlist
entries, because they short-circuit.

**Expressions are pure.** They read signals and return a value. Writing a signal
is a command, per ADR-0007.

## Considered options

- **Bare signal references, no computation.** `::rz/show :editing?` and nothing
  more. Rejected: negation would require the server to send a redundant signal, or
  a round trip, for something the client plainly knows.
- **A vector-shaped edn dsl**, `[:not [:sig :editing?]]`. Rejected: it is a Lisp
  wearing a disguise. Clojure already has a notation for calling a function on
  arguments, and the audience already reads it.
- **Registered cljs functions named from the markup.** `::rz/class {:active
  :app/menu-open?}` with the predicate defined in a cljs namespace. Rejected as
  the primary mechanism: every condition would be defined in two places, and the
  markup would stop saying what it means. It stays available, because an
  allowlisted function is exactly this, so an application that wants a named
  predicate registers one.
- **JavaScript expression strings, as Datastar does.** Rejected: it puts an
  unlintable, un-REPL-able second language inside Clojure source, and it needs
  `unsafe-eval` in the content security policy. The whole reason rhizome can do
  better is that its markup is already data in the same language as its logic.

## Consequences

- **Dependencies are static.** Walking the form for symbols in operand position
  yields exactly the signals a binding reads, before it ever runs. Datastar scans
  its expression strings for `$name` to get the same answer, and an opaque
  registered function could not answer it at all without runtime tracking. This
  may mean bindings need no dynamic dependency tracking, though computed signals
  still might.
- **No `eval`, no interop, no property access.** A symbol is a signal or an
  allowlisted function, and anything else is an error at mount. The attack surface
  is the allowlist, and it is a value an application can read.
- **Errors land at mount, not on click.** An unknown symbol or a bad arity is
  found when the binding is first read, so a typo surfaces on page load.
- The quoting is visible in the hiccup. `'(not editing?)` carries a quote mark
  that `[:not [:sig :editing?]]` would not. Hiccup helpers can hide it where it
  reads badly.

## Open questions

- **Hand-written interpreter, or SCI?** SCI is built for precisely this: a
  configurable Clojure interpreter for cljs with an explicit allowlist of
  namespaces and bindings. It would deliver far more than the allowlist needs, at
  a bundle cost nobody has measured here. htmx ships around 14kb gzipped and
  Datastar around 11kb; a hypermedia runtime that costs several times that is a
  different product. A hand-written interpreter over the read form is on the order
  of a hundred lines and covers the operator set above.

  **Resolve this with a spike before the expressions milestone starts.** Build the
  allowlist both ways, run `:advanced` compilation, and compare gzipped output.
  The decision is a number, and no one has the number yet.
- What convention marks a client-private signal, kept out of requests? ADR-0007
  raises the same question from the wire side. Both want one answer.
