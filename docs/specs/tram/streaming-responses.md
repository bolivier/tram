# Streaming responses

## Status

drafted, not implemented. Decided by ADR-0009, ADR-0010, ADR-0011, and ADR-0012.

## Summary

How a Tram handler returns a stream of events instead of a body, and how those
events reach the wire as `text/event-stream`.

The client half is `docs/specs/rhizome/03-server-sent-events.md`. This document
stops at the frame.

## The handler

```clojure
(ns app.handlers.import-handlers
  (:require [clojure.core.async :as async]
            [tram.sse :as sse]))

(defn run-import [{:keys [params] :as req}]
  {:stream (sse/stream req
             (fn [ch]
               (doseq [row (import/rows (:id params))]
                 (async/>!! ch (sse/morph [:li {:id (str "row-" (:id row))} "imported"])))
               (async/>!! ch (sse/morph [:div#status "Done"]))))})
```

`:stream` is a content key beside `:hiccup`, `:html`, and `:body`, and only one
of the four may be set. `response-content-schema` in
`tram.rendering.template-renderer` gains it, and `owns-body?` returns true for
it, which is what makes page wrapping and template rendering step aside.

Nothing on the route declares that it streams. The response decides, so the same
endpoint can answer with html to one request and a stream to the next.

### `sse/stream`

```clojure
(sse/stream req produce!)
(sse/stream req opts produce!)
```

`produce!` takes the channel and puts events on it. `sse/stream` does the rest:

1. Makes the channel. The buffer is 16 by default, `:buffer` in `opts` overrides.
2. Runs `produce!` on `async/thread`, with `*req*` and `*current-user*` bound.
3. Closes the channel when `produce!` returns, including when it throws.
4. Returns the channel.

This exists because a handler cannot fill its own channel in place. `>!!` blocks
once the buffer is full and nothing drains the channel until the handler has
returned, so the handler deadlocks on the first event past the buffer. Owning the
thread is the whole reason the helper exists, so it takes the body rather than
returning a bare channel.

It binds the request vars because a new thread inherits no dynamic bindings, and
producer code reads `*current-user*` the way view code does.

A handler with a producer of its own skips the helper and returns its channel.
Then it owns the thread and the `async/close!` too.

**Never produce events in a `go` block.** A `go` block runs on a small fixed pool
and blocking I/O inside one starves it. `sse/stream` uses `async/thread`, which is
the main reason to use it rather than roll your own.

## Stream sources

```clojure
(defprotocol StreamSource
  (drain! [source emit!]
    "Calls `emit!` with each event, in order, until the source is spent.
     Returns when it is. Blocking."))
```

Two implementations ship.

| Source          | For                                             |
|-----------------|-------------------------------------------------|
| A channel       | Events produced as work happens.                |
| A seq           | Events already known. Lazy seqs stream lazily.  |

`drain!` is called on a virtual thread, so a blocking implementation is the
expected kind. The channel implementation uses `<!!` and lives in its own
namespace so `tram.sse` itself does not require core.async.

`emit!` returns `false` once the client has gone. A source that can stop early
should. The channel implementation closes its channel when it sees `false`, so a
producer's `>!!` returns `false` and an ordinary `while` loop ends.

## Events

An event is a command map, per ADR-0010.

```clojure
{:command :dom/morph :dom/content [:li#row-4 "imported"]}
```

Constructors cover the commands Tram ships. An application writes the map
directly for a command it registered itself.

```clojure
(sse/morph hiccup-or-html)   ;; => {:command :dom/morph :dom/content ...}
(sse/remove-element id)      ;; => {:command :dom/remove :dom/id id}
```

`:dom/content` takes hiccup or a finished html string, matching `:hiccup` and
`:html` on an ordinary response. An event may name a view instead of carrying
content:

```clojure
(sse/render :view/import-row {:row row})
;; => {:command :dom/morph :template :view/import-row :locals {:row row}}
```

The handler never builds html and never builds a frame.

Every element in a rendered event needs an id, because ADR-0003 makes id the only
targeting mechanism. An event whose content has no id will be dropped by the
client and reported there. Tram does not check for it on the way out; that is one
more traversal of every fragment to catch a mistake the client already names.

## Rendering an event

Per ADR-0012, an event is a partial response that arrives late. The transport
builds a ctx from the request that opened the stream and runs that route's
outgoing interceptors over it.

```clojure
{:request    <the request that opened the stream>
 :response   {:hiccup (:dom/content event) :locals (:locals event)}
 :layouts    <from the opening ctx>
 ::sse/event true}
```

`:dom/content` becomes `:hiccup`, or `:html` when it is already a string. An
event naming a `:template` sets that instead and lets the render group resolve the
view, exactly as it resolves one for a handler.

The transport then takes `[:response :body]` off the result and puts it back on
the event as `:dom/content`. What reaches the frame is a string.

### Which interceptors run

All of them, on the `:leave` side only. The transport reads the route's
interceptors off the request, reverses them, and calls each `:leave` in turn.
That is the order a real response goes out in.

Nothing opts in and nothing is filtered by name. The transport keeps only
`[:response :body]`, so an interceptor that sets a header, a status, or a cookie
on the way out cannot affect an event. Only the ones that build a body can, and
those are the render path.

The ones that would be wrong exclude themselves for reasons that predate
streaming:

| Interceptor             | Per event                | Why                                            |
|-------------------------|--------------------------|-------------------------------------------------|
| `:tram/render-template` | Renders hiccup to html   | The one doing the work.                         |
| `:tram/expand-headers`  | Expands route references | Wanted.                                         |
| `:tram/wrap-page`       | Nothing                  | `needs-full-page?` is false for a rhizome request. |
| `:tram/format`          | Nothing                  | Muuntaja has no encoder for `text/html`.        |
| Layouts                 | None                     | `uses-layout?` is `(not (rhizome-request? req))`. |

An application's render interceptor is on the route, so it runs. There is nothing
to register.

The ctx carries `::sse/event true`. An interceptor whose `:leave` has a side
effect that must not repeat per event reads it and returns early:

```clojure
{:name  :app/audit
 :leave (fn [ctx] (if (::sse/event ctx) ctx (audit! ctx)))}
```

Tram ships no interceptor that needs this. It is here for the application that
commits a transaction, records a metric, or writes an access log on the way out.

### When rendering fails

A render failure drops one event and the stream keeps going. Tram logs the
exception with the event, and reads the next one. This matches the client, which
drops one bad frame rather than the connection.

## The frame

```
event: dom/morph
data: {:dom/content "<li id=\"row-4\">imported</li>"}

```

The event name is the command keyword with no leading colon. The data is the
command map without `:command`, as `pr-str` edn, on one line. A frame ends with a
blank line.

### Response head

Tram sets these. A handler setting any of them is overridden.

| Header               | Value               | Why                                    |
|----------------------|---------------------|-----------------------------------------|
| `content-type`       | `text/event-stream` | What the client dispatches on.          |
| `cache-control`      | `no-cache`          | No intermediary may hold a stream.      |
| `x-accel-buffering`  | `no`                | nginx buffers the whole response without it. |

Status is 200. A handler that wants to fail fails the ordinary way, with a status
and a body, before it returns `:stream`. There is no way to fail a stream after
its head has been sent, which is a property of the format and not a choice.

### Keep-alive

A comment frame goes out every 25 seconds that produce no event.

```
: keep-alive

```

Proxies and load balancers close idle connections, commonly at 30 or 60 seconds.
The client discards comment frames per the format, so this costs nothing but the
bytes.

## The transport

One interceptor, `stream-response-interceptor`, in the render concern-group. It
sits outermost, outside `:tram/format`, so its `:leave` runs after every other
one in the group and nothing downstream can encode, wrap, or re-render what it
produced.

On a response carrying `:stream`:

1. Open the server's async channel from the raw request.
2. Send the head with `close?` false.
3. Build the per-event render fn from the ctx, once, not per event.
4. Start a virtual thread. Call `drain!` on the source with an `emit!` that
   renders, frames, and writes one event.
5. Register a close callback. When the client goes, `emit!` starts returning
   `false`.
6. When `drain!` returns, close the connection.

Step 3 is where the render group is collected and the ctx template is built.
Doing it once means an event costs the render pass and nothing else.

The write side is a protocol too, so the drain loop names no server:

```clojure
(defprotocol EventEmitter
  (send-event! [emitter event] "Frames and writes one event. False once closed.")
  (close-stream! [emitter] "Ends the response."))
```

http-kit is the one implementation. It exists so the drain loop, the framing, and
the keep-alive timer are testable against a recording emitter with no socket, and
so a Jetty or ring async adapter is a new namespace rather than a rewrite.

### Threads

The handler returns immediately. The request thread is free the moment the
interceptor hands off. Only the virtual thread running `drain!` outlives the
response, and virtual threads are cheap enough that a thousand open streams is a
thousand parked threads and no pool exhaustion. This needs JDK 21. Tram is on 26.

## What a stream does not change

- **CSRF.** A streaming POST carries its token like any other. The security
  concern-group runs before the response exists.
- **`rhizome-request?`.** A stream request sets the header like any other. It is
  the same fetch.
- **Interceptors.** Every `:enter` runs normally, once, for the request that
  opens the stream. On the `:leave` side, `owns-body?` makes the render group
  step aside for the response itself. Every `:leave` then runs again per event,
  against a synthetic ctx. Nothing runs the `:enter` side twice.

## Out of scope

- **Reconnection and `Last-Event-ID`.** ADR-0009 defers it. A server that could
  answer a resume request needs an event log, and nothing has one.
- **Signal events.** `:signal/set` down a stream is milestone 7's business, and
  it needs no transport change: it is one more command name.
- **Backpressure toward the client.** The client reads as fast as it can and
  cannot ask for less. The channel's buffer bounds the producer, not the socket.

## Open questions

- **How does a failed producer tell the client?** An exception mid-stream gets
  logged and the connection closed. To the client that is indistinguishable from
  a clean finish. Options are a terminal `event: tram/error` frame, or closing
  abnormally so the client's reader rejects. The first is easy and the second is
  more honest. Decide when something real can fail mid-stream.

- **Should Tram cap concurrent streams per session?** Nothing stops a page from
  opening one stream per element and nothing on the server declines. Virtual
  threads make the server side cheap. The client hits the HTTP/1.1 connection cap
  long before the server notices, which argues the cap belongs on the client or
  nowhere.

- **Does `emit!` returning `false` reach a seq source usefully?** A channel
  producer parks on `>!!` and learns. A lazy seq is pulled by `drain!`, so it
  learns only when `drain!` stops pulling, which is the right behaviour but not an
  obvious one.

- **Where on the request does the chain live?** `[::r/match :data :interceptors]`
  holds what the route declared, merged down the tree by reitit. The compiled
  queue on `:result` is the other candidate and is what actually ran. They can
  differ, because reitit compiles an interceptor and may replace it. The compiled
  one is the honest answer to "what runs on a response", so prefer it and confirm
  it is reachable from the request. Check this against reitit before building on
  it.

- **Does an event's `:locals` merge with the opening response's locals?** A
  handler sets locals for its own response. An event that names a view supplies
  its own. Inheriting looks convenient and hides where a value came from.
