# A stream event is a response through the outgoing interceptors

## Status

accepted

## Decision

Tram renders every stream event by building a synthetic ctx from the request that
opened the stream, and running that route's outgoing interceptors over it.

```clojure
{:request  <the request that opened the stream>
 :response {:hiccup [:li#row-4 "done"] :locals {}}
 :layouts  <from the opening ctx>}
```

Outgoing means `:leave`. Every interceptor on the route has its `:leave` called,
in the reverse of the order the route lists them, which is the order a real
response goes out in. There is no subset and nothing opts in.

The transport then takes `[:response :body]` off the result. Everything else the
chain produced is discarded.

The interceptors come off the request, which is why the request is the argument
this needs.

## Why running all of them is safe

Because only the body survives. An interceptor whose `:leave` sets a header, a
status, or a cookie, or writes a log line about the response, has no effect on an
event: the transport does not read those. The interceptors that can change an
event are the ones that build a body, and those are the render path.

Each of the ones that would be wrong excludes itself already, for reasons that
have nothing to do with streams:

| Interceptor             | What happens per event      | Why                                          |
|-------------------------|-----------------------------|-----------------------------------------------|
| `:tram/render-template` | Renders hiccup to html      | The one that does the work.                   |
| `:tram/expand-headers`  | Expands route references    | Wanted. Event content holds them.             |
| `:tram/wrap-page`       | Nothing                     | `needs-full-page?` is false for a rhizome request. |
| `:tram/format`          | Nothing                     | Muuntaja has no encoder for `text/html`.      |
| Layouts                 | None applied                | `uses-layout?` is `(not (rhizome-request? req))`. |

None of that is a special case written for streaming. A stream request is a
rhizome request and an event is a partial, so the existing rules land on the
right answer on their own.

## Considered options

- **An opt-in marker on each interceptor**, `:tram/stream-event true`, with Tram
  marking its own. Rejected. It invents a concept to name a set that "the
  outgoing interceptors" already names exactly. It also fails in the quiet
  direction: an application adds a render interceptor, forgets the key, and its
  work applies to every html response and silently not to streams. Two rendering
  paths that drift is the failure this whole ADR exists to prevent, and an opt-in
  marker builds it back in.

- **Render in the transport with a hardcoded call to `renderer/render`.** The
  smallest thing that gives handlers hiccup. Rejected for the same drift: it
  freezes the pipeline at what Tram ships, and an application's own render
  interceptor never runs.

- **Render on the producer's side, with a transducer on the channel.** Rejected
  because it serves one source. A seq source would yield unrendered hiccup and a
  custom source would have to remember. Rendering belongs where every source
  passes through, which is the transport. The transport holds the whole ctx, so
  it has the request the interceptors come off.

## Consequences

- **A handler writes hiccup and never writes html.** This was the requirement
  that produced this ADR. `(sse/morph [:li#row-4 "done"])` is the whole of it.

- **An application's render interceptor reaches stream events with no
  registration at all.** It is on the route, so it runs. This is the property the
  marker design could not give.

- **An event can name a view instead of carrying content.** The chain resolves
  `:template` and `:locals` the way it does for a handler's response.

  ```clojure
  (sse/render :view/import-row {:row row})
  ```

- **A `:leave` with a side effect now runs once per event.** This is the real
  cost, and it is the one thing to watch. Committing a transaction, recording a
  metric, or writing an access log on the way out are all things that would fire
  per event rather than per response. Tram ships no such interceptor. An
  application that has one reads `::sse/event` off the ctx and returns early.

  ```clojure
  {:name  :app/audit
   :leave (fn [ctx] (if (::sse/event ctx) ctx (audit! ctx)))}
  ```

  This is the inverse of the rejected marker and costs far less. The common case,
  a rendering interceptor, needs nothing. Only an interceptor that must not
  repeat says so, and it is in the best position to know.

- **Rendering costs one chain pass per event.** A thousand events run the chain a
  thousand times. That is the same work a thousand partial responses would do, on
  one virtual thread instead of a thousand request threads. Measure before
  optimising. The chain is collected once when the stream opens, not per event.

- **A render failure kills one event, not the stream.** The transport catches,
  reports, and reads the next event. This matches the client, which drops one bad
  frame rather than the connection.
