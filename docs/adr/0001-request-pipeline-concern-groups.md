# Request pipeline is composed from concern-groups, not a shipped default chain

## Status

accepted

## Decision

Tram ships no opaque default interceptor chain and no `default-interceptors`
function. Instead it provides a small set of bidirectional **concern-groups**
(`wire-format`, `security`, `render`, plus `exception`) that each flatten into
their constituent interceptors; the application composes them into a short,
hand-written, plain vector, and `tram-router` flattens and validates that vector
before building the reitit router. Ordering constraints ride on individual
interceptors as an optional `:must-run-after` key and are verified at assembly,
throwing a precise error when a named dependency is absent or ordered later than
the interceptor that requires it.

## Considered options

- **Opaque default chain** — `tram-router` injects the standard interceptors,
  configured through option knobs. Rejected: you cannot anticipate every
  insertion point a user needs, and inserting into or removing from a hidden
  chain requires a knob for every spot. The order and contents leave the
  application's sight.
- **Bare default interceptor vector** the app splices by index. Rejected:
  positional access (`subvec`) is brittle — adding one interceptor to the
  default silently shifts every caller — and there is no ergonomic edit verb.
- **Name-anchored splice combinators** over a flat vector. Rejected: introduces
  bespoke edit semantics for a list the user wanted to keep plain. The
  concern-groups shrink the app's chain to ~4 entries, so between-group edits are
  ordinary vector edits and the combinators earn nothing.

## Consequences

- Interceptor leaf names (all under `:tram/*`) and `:must-run-after` are public
  contract. Renaming or removing a named interceptor is a breaking change — but a
  loud one (assembly throws) rather than a silent reorder.
- A fix that lives inside a concern-group (e.g. a new security interceptor)
  reaches every application that composes that group and did not inline it,
  closing the gap where a hand-copied chain silently rots on upgrade. CSRF
  landing after apps were scaffolded is the motivating instance.
- Interceptors live with their concern (CSRF in `tram.csrf`, wire concerns in a
  new `tram.wire-format`), not in a single interceptors module. The
  per-interceptor `:must-run-after` is what makes that safe: the pipeline's
  correctness is verified at assembly rather than read from one central file.
- The `format` transport codec is applied outermost — before `exception` — and
  is deliberately *not* part of the `wire-format` group. `exception` must wrap
  the coercion interceptors to catch coercion failures, but `format` must sit
  outside `exception` so it encodes the error responses `exception` produces
  (e.g. a coercion `:error` handler that re-renders a form as hiccup). The
  exception boundary therefore cuts through the wire concern: `format` outside,
  parsing/coercion inside.
