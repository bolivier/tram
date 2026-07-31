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
  [...] :keydown [...]}}`. Rejected: the trigger stops being visible in the
  attribute name, and it nests one level deeper for the common case of a single
  trigger. It has no scanning advantage, see Consequences.
- **Keep tag inference**, as the current runtime does: `BUTTON` to click, `FORM` to
  submit, `INPUT` by its type across seven cases. Rejected: it guesses, and a `div`
  falls through to click, which is wrong more often than not. Datastar does not
  infer and does not appear to suffer for it. Rhizome can supply the same brevity
  in hiccup helpers, which infer nothing because the helper's own name says the
  trigger.

## Consequences

- **Mount uses `querySelectorAll` over a selector list built from the config.**
  Css has no way to match an attribute by name prefix, since `[attr^=value]`
  matches the value. It does not need one. The set of trigger names is known at
  `start!`, so rhizome joins them into one selector, `[rhizome_core_on___click],
  [rhizome_core_on___submit], ...`, and lets the browser's selector engine do the
  work. Bindings scan the same way, from the keys of the binding registry. A
  `MutationObserver` covers elements added later, and subsumes the question of
  which idiomorph callbacks fire on which nodes.
- **The trigger set is closed, but it is derived rather than written down.** It is
  the keys of a registry, extended the same way commands are. This is not the old
  runtime's `events` map, which hardcoded thirty rhizome keywords, mapped them to
  dom strings for no reason, and rejected everything else with `"Tried to use
  unknown event type"`. That map goes, along with `get-inferred-event`, its nested
  `case` tables, and the `:on` key inside a directive.
- **An unregistered trigger is silently dead.** `::on/clik` matches no selector, so
  nothing mounts and nothing complains. A scan for known names cannot report a name
  it does not know. This is the real cost of enumerating, and it is worth one
  development-mode walk at `start!` that reports any `rhizome_core_on___*`
  attribute the config does not cover.
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
