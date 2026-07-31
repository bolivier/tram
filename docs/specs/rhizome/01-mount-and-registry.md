# 01, mount and the command registry

## Status

drafted, not implemented. Decided by ADR-0004, ADR-0005, and ADR-0006.

## Summary

How a directive gets from an attribute in the document to a running command, and
how an application adds commands of its own.

## The config

```clojure
{:commands {:http/post {...} :dom/morph {...}}
 :triggers #{:click :submit :input ...}
 :bindings {}
 :on-error (fn [error] (js/console.warn ...))}
```

| Key         | What                                                            |
|-------------|-----------------------------------------------------------------|
| `:commands` | Command keyword to command definition.                          |
| `:triggers` | The event names rhizome scans for. Becomes the mount selector.   |
| `:bindings` | Binding attribute to binding definition. Empty until milestone 6. |
| `:on-error` | Where every rhizome failure goes. One sink, one override point.  |

`:triggers` and the keys of `:bindings` are the only source of the attribute names
rhizome looks for. Nothing else may spell one.

## The command definition

```clojure
{:key        :http/post
 :on-trigger (fn [ctx] ...)
 :on-unmount (fn [ctx] ...)
 :schema     [:map [:http/url :string]]}
```

| Key           | Required | What                                                       |
|---------------|----------|------------------------------------------------------------|
| `:key`        | yes      | The command keyword a directive names.                     |
| `:on-trigger` | yes      | Runs when the trigger fires. The whole of what a command does. |
| `:on-unmount` | no       | Releases what `:on-trigger` returned, when the element goes. |
| `:schema`     | no       | Malli schema the command's map must satisfy.               |

There is no `:on-mount` hook and no default trigger on a definition. ADR-0006
makes mount a trigger, `::on/mount`, so a command that must run at mount is placed
there by the page author. That leaves one entry point per command.

`:on-trigger` returns mount state, and rhizome hands it back to `:on-unmount`.
That is where an abort controller, an interval id, or a stream lives.

`:schema` is checked at mount, not at trigger, so a malformed command is reported
once on page load rather than on every click. A command that fails its schema does
not mount, and the failure goes to `:on-error`.

## The context map

Every lifecycle fn takes one map.

```clojure
{:element     el
 :directive   [{:command :http/post :http/url "/save"}]
 :command     {:command :http/post :http/url "/save"}
 :trigger     :click
 :event       e    ;; the dom event, nil for ::on/mount and on unmount
 :mount-state x    ;; what :on-trigger returned, only on unmount
 :config      config}
```

`:config` is present so a command can run another one. `:http/get` runs
`:dom/morph` with the html it received.

```clojure
(rz/run! ctx {:command :dom/morph :dom/content body})
```

`run!` looks the command up in the ctx's config and calls its `:on-trigger`. It is
the seam ADR-0004 promises.

## Triggers

An attribute in the `rhizome.core.on` namespace is a trigger. Its name is the
event.

```clojure
[:button {::on/click     [{:command :http/post :http/url "/save"}]
          ::on/mouseenter [{:command :http/get  :http/url "/preview"}]}]
```

The value is a vector of commands, run in order. A directive's commands are not
sequenced against each other: rhizome starts each in turn and does not wait for an
async one to settle before starting the next. Sequencing waits for a real case.

`::on/mount` is the one synthetic trigger. It runs once when rhizome mounts the
element, with `:event` nil. Every other name is passed to `addEventListener`
unchanged, so custom events work with no table.

### Modifiers

A directive may be a map instead of a vector, carrying modifiers beside its
commands.

```clojure
{::on/keydown {:key      "Enter"
               :prevent  true
               :debounce 300
               :do       [{:command :http/post :http/url "/search"}]}}
```

| Modifier   | Effect                                                     |
|------------|------------------------------------------------------------|
| `:key`     | Run only when `.key` on the event matches.                 |
| `:prevent` | `preventDefault` before running.                           |
| `:stop`    | `stopPropagation` before running.                          |
| `:once`    | Unmount the directive after its first run.                 |
| `:debounce`| Milliseconds. Run after the event stops arriving.          |
| `:throttle`| Milliseconds. Run at most once per window.                 |

A vector directive is the same as a map with only `:do`. Modifiers govern when
commands run and never change what they do, which is why they are not commands.

The old runtime had `:on/key` defined twice, once as a filter and once as a
wrapper, and an `:on/debounce` wrapper that did not debounce. One list, one
mechanism, and each modifier tested.

## Mounting

`start!` builds one selector from the config and hands it to `querySelectorAll`.

```
[rhizome_core_on___click], [rhizome_core_on___submit], [rhizome_core_on___input], ...
```

The names come from `:triggers` on the config, so the selector is derived at start
rather than written down. Spell each one with `rhizome.html/kw->string`, never as
a literal. The old runtime hardcoded `"rhizome_core___on"` in four places.

1. `start!` computes the selector and runs it from `document.body`.
2. For each match, read the trigger attributes it carries.
3. Parse each value as edn. A parse failure goes to `:on-error` and that attribute
   does not mount, leaving the rest of the element alone.
4. Check each command against its definition's `:schema`.
5. Add a listener, or for `::on/mount`, run once.
6. Record what mounted on the element, so unmount can find it and so a second scan
   over the same element does nothing.

A `MutationObserver` on `document.body`, subtree and childList, runs the same
selector over each added subtree and unmounts what leaves in a removed one. This
covers everything morph does to the tree, which is why milestone 2 needs no
idiomorph callback to rewire.

### The trigger list

`:triggers` on the config is a set of event names. The default holds the standard
dom events, so nobody registers `click`. An application adds its own for a custom
event:

```clojure
(rz/register-trigger config :app/order-placed)
```

A generous default costs a longer selector string and nothing else. Whether a
selector list of that size is worth trimming is a question for a measurement, not
an opinion. Measure before shortening it.

**An unregistered trigger is silently dead.** `::on/clik` matches no selector, so
nothing mounts and nothing says so. A scan for known names cannot report a name it
does not know. Cover it with a development-mode pass at `start!` that walks once
looking for `rhizome_core_on___*` attributes the config does not cover, and sends
each to `:on-error`. It runs once on page load, not per morph.

## The api

```clojure
(rz/default-config)
;; => the config rhizome ships, with its own commands registered

(rz/register config definition & more)
;; => a new config. Pure. A duplicate :key replaces the earlier definition.

(rz/start! config)
;; => scans the document, starts the observer, remembers the config
```

```clojure
(-> (rz/default-config)
    (rz/register {:key        :app/confetti
                  :on-trigger (fn [{:keys [element]}] (fire! element))})
    (rz/start!))
```

`start!` stores the config so the observer can reach it. Calling it twice is an
error through `:on-error`. The drop-in script calls `(rz/start! (rz/default-config))`
on `DOMContentLoaded`, so a user who registers nothing writes no cljs at all.

## Hiccup helpers

ADR-0006 puts the brevity here rather than in the attribute vocabulary. These are
server-side functions returning an attribute map, so they cost nothing at runtime.

```clojure
(rz/post :route/save)
;; => {::on/click [{:command :http/post :http/url "/save"}]}

[:button (rz/post :route/save) "Save"]
```

One helper per http verb, each defaulting to `::on/click`, plus `rz/submit`
defaulting to `::on/submit`. A second arity takes the trigger. They merge, so an
element can take a helper's map and its own attributes.

Exact set and arities are open. They are pure functions over data, so they can
follow the runtime rather than block it.

## Out of scope

- What the http commands do. Milestone 2.
- Bindings, and the `:bindings` registry key defined here as empty. Milestone 6.
- Sequencing commands within a directive, and any await.

## Open questions

- **Does `:once` unmount, or just stop listening?** Unmounting frees the record on
  the element, but a later morph re-adding the same attribute would mount it
  again, which is probably right and possibly surprising.
- **Should a schema failure block the element or the command?** Blocking just the
  bad command leaves an element half-live, which may be harder to debug than a
  dead one.
- **Does the observer need to be on `document.body`, or per mounted root?** One
  observer is simpler. A per-root observer would let a test mount a detached
  subtree, which one global config already makes awkward.
