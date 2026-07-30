# Directives target by id

## Status

accepted

## Decision

Id is the only way a rhizome directive names a target.

`:dom/morph` takes an html fragment. Each top level element in that fragment
replaces the element on the page whose id equals the fragment element's own id.
The directive carries no target of its own. The returned html says where it goes.

Every other dom command takes an optional `:dom/id`. Absent, the command acts on
the element its directive sits on.

The `:ident` key is removed, along with the `get-target` multimethod and its
`:id`, `:closest`, `:previous`, `:next`, `:this`, and `:end-of` methods. The
`precedes?` and `follows?` helpers exist only to serve those methods and go with
them.

This extends ADR-0002, which already states the same-id placement rule for
partials. ADR-0002 records what a partial does. This one records that rhizome
offers no alternative.

## Considered options

- **Keep the selector language for the non-morph dom commands.** Morph would be
  same-id, while `:dom/add-class` and `:dom/remove` kept `:closest`, `:previous`,
  and the rest. Rejected: the glossary would have to teach two targeting rules for
  one runtime, and a reader could not tell from a directive's shape which rule
  applied. The selector methods also carry the whole `precedes?`/`follows?`
  machinery for a handful of commands nobody has asked to target remotely.
- **Replace `:ident` with a css selector string.** One key, `:dom/target
  "[data-panel] > .row"`, resolved with `querySelector`. Rejected: it is strictly
  more power than same-id, so the server can stop putting ids on partial roots,
  and then morph has nothing to match on. It also couples markup structure to the
  directive, which is the coupling ids exist to avoid.
- **Keep `:end-of` alone**, as the one way to append. Rejected: append is the only
  reason it exists, and a server can append by returning the container. Keeping it
  means keeping the `:ident` vector shape that everything else drops.

## Consequences

- **Append goes away.** `:end-of` was the only command that added an element
  rather than replacing one. A server that wants to add a row now returns the
  whole table with an id, and morph diffs it. Idiomorph already handles that well.
  If a real case needs append later, it gets its own command and its own spec.
- **Every top level element in a partial must carry an id.** This is now a hard
  requirement on the server, not a convention. A partial element with no id
  cannot be placed and is an error.
- **The docsite contradicts this decision.** `counter-example` in
  `docsite/src/tram_docs/views/examples_views.clj` passes `:ident [:this]`, and
  `rhizome-command-attribute-survives-the-browser-test` in
  `test/main/tram/html_test.clj` uses `:ident [:closest "[data-panel]"]`. Both
  change when the removal lands.
- Shadow dom still works. The lookup runs from the root node of the element the
  directive sits on, so a directive inside a shadow root finds ids in that root.
