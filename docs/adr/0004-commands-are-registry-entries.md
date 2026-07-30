# Commands are registry entries, not multimethods

## Status

accepted

## Decision

Rhizome resolves a command through a registry: a map from command keyword to
command definition. An application builds a config, registers its commands onto
it, and hands the config to `start!`.

```clojure
(-> (rz/default-config)
    (rz/register {:key        :app/confetti
                  :on-trigger fire-confetti!})
    (rz/start!))
```

The `rhizome.command/execute` multimethod and its `defmethod`s are removed.

This departs from the repository rule in `CLAUDE.md` that extension points use
protocols, and failing that Methodical multimethods. The reasons are below.

## Considered options

- **Methodical multimethod on `:command`.** The current design, with CLOS-style
  dispatch added. Rejected for two reasons. First, a command is a bundle of fns,
  not one fn: it has mount, trigger, and unmount behaviour plus a schema. One
  multimethod carries one fn per dispatch value, so this shape needs four parallel
  multimethods that must be kept in sync by hand, and nothing checks that they
  are. Second, a multimethod's table is a global mutated by namespace load order.
  In a browser bundle that order is a property of the require graph, which is a
  poor place to keep a user's command set.
- **A protocol.** Rejected: a command is data plus fns, not a type. There is no
  value to extend the protocol onto.
- **A registry plus a multimethod**, registry for lifecycle and multimethod for
  the trigger fn. Rejected: two lookup mechanisms for one concept, and a reader
  has to know which half a given command lives in.

## Consequences

- **A registry is data, so it is inspectable and testable.** An application lists
  its commands, diffs two configs, or tests a command definition as a map without
  touching global state. A test needs no fixture to reset a dispatch table.
- **Requiring a namespace no longer registers a command.** Today a `defmethod` in
  any loaded namespace joins the command set. Registration becomes explicit at the
  start call. This is the intended trade: load order stops being load-bearing.
- **The registry must reach a running command.** A command that runs another one,
  as `:http/get` runs `:dom/morph`, receives the config in its context map. The
  seam is `run!`.
- `rhizome.command` loses its only contents and goes away.
- The rule in `CLAUDE.md` still holds for the rest of the framework. This is one
  documented exception, made because the extension point carries several fns per
  key.
