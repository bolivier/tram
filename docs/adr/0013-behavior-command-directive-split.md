# Behavior, command, and directive name three different things

## Status

accepted

## Decision

The runtime's vocabulary splits three ways. A **directive** is one of an
element's rhizome attributes, key and value together. A **command** is the map
`execute` runs. A **behavior** is the definition registered for one attribute:
what an element does at mount and which event it listens for. The value half of
a directive is its **payload**.

The old names go. `rhizome.triggers` becomes `rhizome.behaviors` and
`rhizome.directives` becomes `rhizome.commands`. "Trigger" now names only the
event that fires a directive.

## Considered options

- **"Trigger" for registry entries.** The old name. Rejected: `bind`, `text`,
  and `show` fire on no event, so most entries were not triggers.
- **"Directive" for both the attribute and the executed map.** Rejected for two
  reasons. Commands outlive attributes: stream events and response handling
  produce them with no attribute in sight. And a payload is not one kind of
  thing: an event behavior carries a command, a binding carries a signal
  reference. One word cannot cover both honestly.
- **"Attachments", "attributes", or "traits" for behaviors.** Rejected:
  "attribute" collides with the dom meaning, and the others say less than
  "behavior" does.

## Consequences

- The glossary entries for directive and command in `CONTEXT.md` change
  meaning. A directive is now the whole attribute, not its value alone.
- ADRs 0003, 0004, 0006, and 0010 predate this split and use "directive" for
  the attribute's value. Read them with this ADR's meanings.
