# Rhizome is the only runtime that produces partials

## Status

accepted

## Decision

Tram supports one client runtime for partial responses: rhizome. A response is a
**partial** when the rhizome runtime asked for it, and a **page** otherwise. That
one test decides both whether layouts apply and whether the page fn wraps the
body. htmx is not a supported runtime, and `htmx-request?` and the `hx-redirect`
form of `redirect` are to be removed rather than deprecated — Tram is early alpha
and owes no compatibility.

## Considered options

- **Support both runtimes**, with a partial detected from either the rhizome or
  the htmx header. Rejected: the two runtimes place content by different rules —
  rhizome replaces the element whose id matches the returned element's own id,
  htmx swaps by `hx-target`/`hx-swap` — so "partial" would name two behaviours
  and the glossary could not state the placement rule. Supporting both also means
  two sets of redirect and error-target conventions for one concept.
- **Keep htmx as a documented interop case**, first-class rhizome plus
  best-effort htmx. Rejected for the same reason at lower volume: the docs still
  have to teach two placement rules, and the framework still carries two headers
  through every rendering decision.

## Consequences

- The htmx-vs-rhizome divergence that exists today is a bug, not a policy. Both
  `uses-layout?` and the page-wrapping check consult only the rhizome header, so
  an htmx response currently receives a full html document wrapped around a
  fragment. Collapsing the two checks into one partial test fixes it.
- **The starter template contradicts this decision and blocks the removal.** It
  loads `htmx.org` and `htmx-ext-response-targets` from unpkg, ships no
  rhizome.js, and its entire authentication flow calls `redirect` for the
  `hx-redirect` header. Deleting the htmx functions requires migrating the
  starter template to rhizome first.
- **Rhizome has no redirect.** Its command set covers dom mutation and the five
  http verbs; there is no navigation command, no `window.location`, no
  `pushState`. A rhizome app cannot currently redirect from a partial, so
  `redirect` has no rhizome-side replacement to migrate onto. That gap must be
  closed before the htmx functions can go.
- `full-redirect` is unaffected. It is a plain 303 with a `Location` header and
  belongs to page responses, which do not involve a client runtime.
