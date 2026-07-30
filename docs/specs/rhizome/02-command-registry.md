# 02, the command registry

## Status

drafted, not implemented. Decided by ADR-0004.

## Summary

An application extends rhizome by registering commands onto a config, then
starting rhizome with that config. A command is a map of a key and lifecycle fns.
The `execute` multimethod goes away.

## The config

```clojure
{:registry  {:http/post {...} :dom/morph {...}}
 :attribute :rhizome.core/do
 :modifiers {}
 :on-error  (fn [error] (js/console.warn ...))}
```

| Key          | What                                                                  |
|--------------|-----------------------------------------------------------------------|
| `:registry`  | Command keyword to command definition.                                |
| `:attribute` | The keyword naming the directive attribute. Defaults to `::rz/do`.    |
| `:modifiers` | What sits between a trigger and its command. Spec 03 owns the shape.  |
| `:on-error`  | Where every rhizome failure goes. One sink, one place to override it. |

The html spelling of the attribute derives from `:attribute` with
`rhizome.html/kw->string`, in one place. Today the string `"rhizome_core___on"`
is written out in four.

## The command definition

```clojure
{:key        :http/post
 :on-trigger (fn [ctx] ...)
 :on-mount   (fn [ctx] ...)
 :on-unmount (fn [ctx] ...)
 :trigger    :event/click
 :schema     [:map [:http/url :string]]}
```

| Key          | Required | What                                                             |
|--------------|----------|------------------------------------------------------------------|
| `:key`       | yes      | The command keyword a directive names.                           |
| `:on-trigger`| one of   | Runs each time the trigger fires.                                |
| `:on-mount`  | one of   | Runs once when rhizome mounts the directive.                     |
| `:on-unmount`| no       | Runs when the element leaves. Receives what `:on-mount` returned. |
| `:trigger`   | no       | The command's default dom event, overridden by the directive's `:on`. |
| `:schema`    | no       | Malli schema the directive must satisfy.                         |

A definition supplies `:on-trigger`, `:on-mount`, or both. A definition with
neither does nothing and is a registration error.

A command with no `:on-trigger` gets no event listener. That is what a mount-only
command like a stream subscription wants.

`:on-mount` returns mount state, and rhizome hands it back to `:on-unmount`. That
is where a connection, an interval id, or an abort controller lives. Spec 05
defines when unmount runs. Until spec 05 lands, `:on-unmount` is accepted,
recorded, and never called.

`:schema` is checked at mount, not at trigger, so a malformed directive is
reported once when the page loads and not on every click. A directive that fails
its schema does not mount, and the failure goes to `:on-error`.

## The context map

Every lifecycle fn takes one map.

```clojure
{:element     el
 :directive   {:command :http/post :http/url "/counter"}
 :event       e     ;; the dom event, nil during mount and unmount
 :mount-state x     ;; what :on-mount returned, only on unmount
 :config      config}
```

`:config` is present so a command can run another command. `:http/get` runs
`:dom/morph` with the html it received.

```clojure
(rz/run! ctx {:command     :dom/morph
              :dom/content body})
```

`run!` looks the command up in the ctx's config and calls its `:on-trigger` with
a ctx carrying the new directive. It is the seam ADR-0004 promises.

## The api

```clojure
(rz/default-config)
;; => the config rhizome ships, with its own commands registered

(rz/register config definition & more)
;; => a new config. Pure. A duplicate :key replaces the earlier definition.

(rz/start! config)
;; => mounts every directive in the document and remembers the config.
```

An application composes them:

```clojure
(-> (rz/default-config)
    (rz/register {:key        :app/confetti
                  :on-trigger (fn [{:keys [element]}] (fire! element))})
    (rz/start!))
```

`start!` stores the config so a mount after a morph can reach it. Calling it
twice is an error, reported through `:on-error`.

The drop-in script's `init` calls `(rz/start! (rz/default-config))`, deferring to
`DOMContentLoaded` as it does today. Nothing about the drop-in path changes for a
user who registers no commands.

## What this removes

- `src/main/rhizome/command.cljs`, the whole namespace. Its only contents are the
  `execute` multimethod.
- Every `defmethod execute` in `core.cljs`, replaced by definitions in the default
  registry.
- The top-level `config` def, with its `:op/attribute`, `:op/handler`,
  `:op/filters`, and `:op/wrappers` keys.
- The four hardcoded `"rhizome_core___on"` strings, in `extract-command`,
  `wire-subtree!`, and `add-on-listener` twice.
- The `"load"` event special case and its `TODO: fix this hack` comment.
  `:event/load` becomes `:on-mount` on the commands that want it.
- The `cljs.core.async` require, which nothing uses.

## Renames

The vocabulary in `CONTEXT.md` binds the names.

| Now                | After                              |
|--------------------|------------------------------------|
| `::rz/on`          | `::rz/do`                          |
| `:op`              | `:command`                         |
| `extract-command`  | `read-directive`                   |
| `wire-el!`         | `mount-el!`                        |
| `wire-subtree!`    | `mount-subtree!`                   |
| `add-on-listener`  | `mount-document!`                  |
| `rhizomeWired`     | `rhizomeMounted`                   |

## Migration

The attribute rename reaches the server and the docsite.

| File                                          | Change                                                        |
|-----------------------------------------------|---------------------------------------------------------------|
| `src/main/tram/html.clj`                      | `defmethod h/emit-attr :rhizome.core/on` becomes `:rhizome.core/do`. |
| `test/main/tram/html_test.clj`                | Two tests. Both write `:rhizome.core/on` in hiccup and both assert on the literal string `rhizome_core___on=`. |
| `docsite/.../views/examples_views.clj`        | Seven directives use `::rz/on` and `:op`.                     |
| `starter-template/`                           | Uses htmx, not rhizome. Check anyway, per `CLAUDE.md`.        |

The rename is mechanical, so it lands in the same commit as the registry. Leaving
both attribute names alive means two spellings in the codebase, and nothing reads
`::rz/on` after this spec.

## Out of scope

- What a modifier is and how `:on/key` and `:on/debounce` work. Spec 03.
- How a trigger is inferred from an element's tag. Spec 03 owns the inference,
  though `:trigger` on a definition is defined here because it is a definition key.
- When `:on-unmount` runs. Spec 05.

## Open questions

- **`:on-trigger` or `:on-listener`?** The original sketch said `:on-listener`.
  `CONTEXT.md` defines a trigger as the dom event that runs a directive, so
  `:on-trigger` reads as "what runs when the trigger fires", and `:on-listener`
  reads as "what runs when a listener happens", which is not a thing. Using
  `:on-listener` means the glossary needs a `listener` entry that pulls against
  `trigger`. Decide before implementation.
- **Can an element hold more than one directive?** One attribute holds one map,
  so today the answer is no, and an element that must post and add a class cannot.
  A vector of directives in the attribute would fix it. Not needed yet, and it
  changes the parse for every directive, so decide it deliberately rather than by
  accident.
- **Does `:schema` belong on the definition or in the registry?** It is on the
  definition here because a command owns the shape of its own arguments. The
  alternative is a separate `:schemas` map on the config, which keeps definitions
  smaller but splits one concept across two keys.
- **Is one global config right?** `start!` stores one, because a page has one
  document. A test that mounts a detached subtree with a different registry has no
  way in. This may want the config threaded through mount rather than stored,
  with `start!` as the convenience that stores it.
