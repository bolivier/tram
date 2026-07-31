# Signals are rhizome's state model

## Status

accepted

## Decision

Rhizome holds client state in signals: named reactive values in a store the
runtime owns. Three things follow.

**The dom is derived, not mutated.** An element's own appearance comes from
bindings that read signals and re-apply when those signals change. Rhizome ships
no imperative command that adds a class, sets an attribute, or writes text. The
old runtime's `:dom/add-class`, `:dom/set-attribute`, and `:dom/clear-attribute`
are not ported.

**A request carries the signals.** The body of a rhizome request is the signal
map, serialized as edn. There is no form scraping. A form field joins the signal
map through a two-way binding, so by the time a request goes out its value is
already state.

**Morph stays imperative.** Morph is server-driven and matches by id, per
ADR-0003. It is not derived from anything, and the html it applies is authored on
the server rather than computed from signals.

Signals are written by commands, never by expressions. `:signal/set` is a command.
An expression only reads. This keeps the expression interpreter of ADR-0008 free
of side effects.

## Considered options

- **Keep the imperative dom commands alongside bindings.** Rejected: two ways to
  set a class, and they fight. A binding re-applies when its signals change and
  would overwrite whatever a command had poked in, at a moment neither the command
  nor the page author chose.
- **Keep form scraping alongside signals.** Rejected for the same shape of reason:
  two mechanisms for the body of one request, and no rule saying which wins when a
  form field is also bound. Datastar deleted form scraping by making bound fields
  signals, and it is the right simplification to copy.
- **Signals as an optional layer** an application turns on. Rejected: the request
  body, the response protocol, and every binding depend on the store existing. An
  optional core is not a core.

## Consequences

- **Http commands cannot send a body until signals exist.** This orders the build
  plan. The first milestone ships bodyless requests, which is enough for the
  docsite counter, and bodies arrive with the signal wire format.
- **The command set is small and stays small.** Rhizome ships the five http verbs,
  `:dom/morph`, `:dom/remove`, and `:signal/set`. Anything about appearance is a
  binding. This is the opposite of the direction htmx and the old rhizome grew in.
- **Bindings are a second extension point**, with their own registry beside the
  command registry of ADR-0004. The config holds both.
- **The server sees the client's whole state on every request.** Tram already
  speaks edn end to end, so this needs no new codec. It does need a story for
  which signals are private to the client. Datastar excludes signals whose names
  start with an underscore. Rhizome should pick a convention deliberately rather
  than inherit that one by default.
- A request growing with the size of the signal store is a real cost at scale.
  Datastar answers it with a per-request filter. Rhizome will need one eventually,
  and does not need one to start.
