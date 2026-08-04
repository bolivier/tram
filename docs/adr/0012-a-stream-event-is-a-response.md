# A stream event is a response through the render concern-group

## Status

accepted

## Decision

Tram renders every stream event by running the render concern-group over a
synthetic ctx built from the original request. The event's content becomes that
ctx's response.

```clojure
{:request  <the request that opened the stream>
 :response {:hiccup [:li#row-4 "done"] :locals {}}
 :layouts  <from the opening ctx>}
```

An event is therefore a partial response that happens to arrive late. It gets
what a partial gets: hiccup rendered to html, route references expanded, no
layout, and no page.

An interceptor opts in by carrying `:tram/stream-event true` on its map. The
transport collects the marked interceptors from the route's data, in chain order,
and runs their `:leave` per event. Tram marks two of its own:

| Interceptor                | Marked | Why                                          |
|----------------------------|--------|-----------------------------------------------|
| `:tram/expand-headers`     | yes    | An event's content holds route references.    |
| `:tram/render-template`    | yes    | This is the one that turns hiccup into html.  |
| `:tram/wrap-page`          | no     | An event is a partial. It never gets a page.  |
| `:tram/format`             | no     | The frame is the encoding. Muuntaja is not.   |

## Considered options

- **Render in the transport with a hardcoded call to `renderer/render`.** The
  smallest thing that works, and it does give handlers hiccup. Rejected because it
  freezes the render pipeline at whatever Tram ships. An application that adds a
  render interceptor gets it on every html response and silently loses it on every
  stream event. Two rendering paths that drift is the failure this avoids.

- **Re-run the route's whole compiled interceptor chain per event.** The literal
  reading of "the same interceptors as an http response". Rejected, and it is
  worth being precise about why. The chain's `:enter` side has already run and
  much of it is not repeatable: a session lookup, a CSRF check, a database
  transaction. The `:leave` side is no safer. `:tram/format` would run muuntaja
  over one event's body and negotiate a content type against an `accept` header
  that asked about the whole response. A stream event needs the render group, not
  the request pipeline.

- **Render on the producer's side, with a transducer on the channel.** Rejected
  because it only serves one source. A seq source would yield unrendered hiccup
  and a custom source would have to remember to render. Rendering belongs where
  every source passes through, which is the transport.

## Consequences

- **A handler writes hiccup and never writes html.** This was the requirement
  that produced this ADR. `(sse/morph [:li#row-4 "done"])` is the whole of it.

- **An event can name a template instead of carrying content.** The render group
  resolves `:template` and `:locals` the same way it does for a handler's
  response, so an event reuses a view by name.

  ```clojure
  (sse/render :view/import-row {:row row})
  ```

- **An application's render interceptor reaches stream events.** It adds
  `:tram/stream-event true` to its map. One key, and no other registration.

- **Opt-in, not opt-out.** An unmarked interceptor never runs per event. The
  reverse default would run sessions, CSRF, and transactions once per event, and
  each of those is wrong in its own way. The cost is that an application with a
  render interceptor and no marker sees its work silently skipped on streams.
  Cover it the way milestone 1 covers unregistered triggers: a development-mode
  check, not a silent difference.

- **Layouts need no special case.** `uses-layout?` is already
  `(not (rhizome-request? req))`, and a stream request is a rhizome request. The
  synthetic ctx carries the real request, so a stream event skips layouts for the
  reason every partial does.

- **Rendering costs one pass per event.** A stream of a thousand events runs the
  render group a thousand times. This is the same work a thousand partial
  responses would do, on one virtual thread instead of a thousand request threads.
  Measure it before optimising it.

- **A render failure kills one event, not the stream.** The transport catches,
  reports through the error handling in `docs/specs/tram/streaming-responses.md`,
  and reads the next event. This matches how the client drops one bad frame.
