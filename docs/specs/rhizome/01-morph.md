# 01, morph replaces by id

## Status

drafted, not implemented. Decided by ADR-0003.

## Summary

`:dom/morph` takes an html fragment and puts it on the page. Each top level
element in the fragment replaces the element whose id is the same. Nothing else
selects a target.

## The directive

```clojure
{:command     :dom/morph
 :dom/content "<div id=\"counter\">3</div>"}
```

`:dom/content` is an html string. It is the only argument.

Morph is rarely written by hand. Its usual caller is an http command, which
receives an html body and runs morph with it. Both paths go through the same
command.

## Behaviour

1. Parse `:dom/content` as html. Take the element children of the resulting
   body. Text and comment nodes at the top level are ignored.
2. For each element, in document order, read its `id` attribute.
3. Find the element with that id. Search from the root node of the element the
   directive sits on, so a directive inside a shadow root finds ids in that root
   and not in the document.
4. Morph the found element with idiomorph, `morphStyle` set to `outerHTML`. The
   fragment element replaces the page element whole, including its attributes.
5. Mount every directive in the subtree idiomorph adds.

A fragment holding several top level elements updates several places on the page
in one call. Each element is matched on its own. One element failing does not
stop the others.

## Errors

Two things go wrong, and both are the server's fault:

- **A fragment element carries no id.** Rhizome cannot place it.
- **No element on the page has that id.** The target is gone, or the server sent
  the wrong id.

Both report through the config's `:on-error`, which spec 02 introduces. Until
that exists, both use `js/console.warn`. Neither throws. One bad element in a
fragment must not stop the rest of the fragment from landing.

The report names the id and the outer html of the offending element. The current
code prints `"Found no target"` with the ident and the content twice, which is
not enough to find the bug and is also a typo.

## What this removes

From `src/main/rhizome/core.cljs`:

- The `:ident` key, on every command that reads it.
- The `get-target` multimethod and its `:default`, `:id`, `:closest`,
  `:previous`, `:next`, and `:this` methods.
- The `:end-of` branch inside `execute :dom/morph`, and with it rhizome's only
  append.
- `precedes?` and `follows?`, which serve only `:previous` and `:next`.
- The `(if (nil? ident) [:id (.getAttribute content "id")] ident)` fallback,
  which becomes the only path.

`get-root-node` stays. It is what makes the shadow root case work.

## What the other dom commands do instead

`:dom/remove`, `:dom/add-class`, `:dom/set-attribute`, and `:dom/clear-attribute`
each take an optional `:dom/id`. Absent, the command acts on the element its
directive sits on.

```clojure
{:command  :dom/add-class
 :dom/id   "panel"
 :dom/class "open"}

{:command   :dom/add-class
 :dom/class "open"}   ;; acts on this element
```

`:dom/add-class` currently reads `:class`, unnamespaced. Move it to `:dom/class`
so every argument of a `:dom/*` command sits in the `dom` namespace, as
`:dom/attribute` and `:dom/value` already do.

## Migration

| File                                              | Change                                                       |
|---------------------------------------------------|--------------------------------------------------------------|
| `docsite/.../views/examples_views.clj`            | `counter-example` passes `:ident [:this]`. Remove it.        |
| `test/main/tram/html_test.clj`                    | `rhizome-command-attribute-survives-the-browser-test` builds a command with `:ident [:closest "[data-panel]"]`. The test is about attribute escaping, so replace the ident with any other key holding a quote. |
| `starter-template/`                               | Check for `:ident`. It ships htmx today, so it likely has none. |

## Out of scope

- Morphing into `head`. Only body elements are matched.
- Any append or prepend. ADR-0003 removed the only one, and a new one gets its
  own command and its own spec if a real case turns up.
- What an http command does with a non-2xx response before it reaches morph.
  That is spec 04.

## Open questions

- **Should a missing id throw in development?** A silent warn is right in
  production. A server that forgets an id gets no feedback in either mode today,
  and the failure is quiet: the page simply does not change. Rhizome has no
  notion of a build mode yet, so this may want a config flag rather than a
  compile-time check.
- **Does idiomorph's `afterNodeAdded` fire often enough to mount everything?**
  The current code mounts from that callback alone. Idiomorph also moves and
  restores nodes, and a moved node with a directive on it is not an added node.
  Confirm against idiomorph's callback set during implementation, and if
  `afterNodeAdded` is not sufficient, mount the whole morphed subtree once after
  the call returns and let the mount flag deduplicate.
