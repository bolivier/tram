# Triggers are attributes and are never inferred

## Status

accepted

## Decision

A directive lives in an attribute named for its trigger. The keyword's namespace
is `rhizome.core.on` and its name is the event.

```clojure
[:button {::on/click [{:command :http/post :http/url "/save"}]}]
;; renders as rhizome_core_on___click="[{:command :http/post ...}]"
```

An element holds one directive per trigger and as many triggers as it needs. The
value is a vector of commands, run in order.

Rhizome never infers a trigger from an element's tag. The trigger is always named.
Besides dom events, rhizome ships one synthetic trigger, `::on/mount`, which runs
when rhizome mounts the element.

## Considered options

- **One attribute holding a map from trigger to commands**, `{::rz/do {:click
  [...] :keydown [...]}}`. Rejected, though it is the closer call of the two. It
  keeps `querySelectorAll` working, which the chosen design gives up. It loses on
  reading: the trigger stops being visible in the attribute name, and it nests one
  level deeper for the common case of a single trigger. Bindings will need a
  document walk regardless, since `::rz/text` and `::rz/class` are separate
  attributes, so the `querySelectorAll` advantage is temporary.
- **Keep tag inference**, as the current runtime does: `BUTTON` to click, `FORM` to
  submit, `INPUT` by its type across seven cases. Rejected: it guesses, and a `div`
  falls through to click, which is wrong more often than not. Datastar does not
  infer and does not appear to suffer for it. Rhizome can supply the same brevity
  in hiccup helpers, which infer nothing because the helper's own name says the
  trigger.

## Consequences

- **Mount cannot use `querySelectorAll`.** No css selector matches "any element
  with an attribute whose name starts with this prefix". Mounting walks the tree
  and inspects each element's attributes, which is what Datastar does. A
  `MutationObserver` covers elements added later, and subsumes the question of
  which idiomorph callbacks fire on which nodes.
- **Any dom event works, including custom ones.** The event name comes from the
  attribute, so the runtime needs no table. The current `events` map lists thirty
  events and rejects everything else with `"Tried to use unknown event type"`. It
  goes, along with `get-inferred-event`, its nested `case` tables, and the `:on`
  key inside a directive.
- **`::on/mount` replaces the `:event/load` special case**, and with it the
  `TODO: fix this hack` branch that calls the handler with a nil event. Mount
  stops being a fake dom event and becomes a real trigger.
- Because mount is a trigger, a command definition needs no separate `:on-mount`
  hook. A command that must run at mount is placed under `::on/mount` by the page
  author, not declared as mount-only by the command author.
- Hiccup helpers carry the brevity: `(rz/post :route/save)` expands to the
  `::on/click` attribute at render time. This is the ergonomic escape hatch htmx
  buys with attribute vocabulary and Datastar buys with expression syntax. Rhizome
  gets it from hiccup being data.
