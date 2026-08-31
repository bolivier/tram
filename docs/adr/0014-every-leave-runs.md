# Every leave runs, even without its enter

## Status

accepted

## Decision

When an `:enter` stage sets a response, `tram.executor` stops running `:enter`
stages but moves the whole remaining queue onto the stack. Every interceptor's
`:leave` then runs, in reverse chain order, exactly as if each had entered.

The contract this creates: **a `:leave` must tolerate a skipped `:enter`.** A
leave that reads state its own enter stashed must handle that state being
absent.

The error path is unchanged. An `:error` walks only the entered stack through
`:error` stages.

## Considered options

- **Pedestal/sieppari semantics** — an early response skips un-entered
  interceptors entirely, both stages. Rejected: Tram's response pipeline
  (rendering, page wrapping, header route expansion, status lowering) is built
  from leave-only interceptors. Under skip semantics, an interceptor that
  early-responds from before the render group emits a response that bypasses
  that whole pipeline. Its hiccup never renders, its `:route/` header keywords
  never expand, its keyword status never lowers.
- **A normalizer seam on the executor** — universal coverage for specific
  concerns (like status lowering) without changing leave semantics. Rejected:
  a new seam with one caller, and it fixes one leave-side concern instead of
  the class.

## Why this is safe here

[ADR 0012](0012-a-stream-event-is-a-response.md) already runs every route
`:leave` over synthetic contexts via `run-leaves`, "as if each had entered".
The framework's own response-side interceptors have no enters to pair with.
This decision promotes that existing assumption from a helper's fine print to
the executor's documented contract.

## Consequences

- The executor deviates from Pedestal and sieppari on this point. The ns
  docstring documents it alongside the other deviations.
- An early response now flows through the full response pipeline. Interceptors
  before the render group may early-respond with hiccup, `:route/` header
  keywords, and keyword statuses.
- Enter/leave pairs that manage per-request state (timers, resources) must
  null-check on leave. None of Tram's shipped interceptors pair stages today.
