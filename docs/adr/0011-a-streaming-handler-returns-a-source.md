# A streaming handler returns a source, and a channel is one

## Status

accepted

## Decision

A handler streams by returning `:stream` on its response, beside `:hiccup`,
`:html`, and `:body` and mutually exclusive with them.

The value is a **stream source**: anything satisfying `tram.sse/StreamSource`.
Tram ships two implementations.

```clojure
;; a channel, for work that produces events as it goes
(defn import-progress [req]
  {:stream (sse/stream req
             (fn [ch]
               (doseq [row (rows)]
                 (async/>!! ch (sse/morph [:li {:id (str "row-" (:id row))} "done"])))))})

;; a seq, for events already known
(defn replay [_req]
  {:stream (map #(sse/morph (row-view %)) (rows))})
```

The channel is the documented shape a generator scaffolds and the docs lead
with. The protocol is what Tram's transport actually talks to.

`core.async` becomes a direct dependency of Tram.

### `sse/stream` owns the thread

`sse/stream` makes the channel, runs the body on `async/thread`, and closes the
channel when the body returns. It hands the channel back.

A handler cannot make a bare channel and fill it in place. `>!!` blocks once the
buffer is full, and nothing drains the channel until the handler returns, so a
handler that fills its own channel deadlocks on the first event past the buffer.
An unbounded buffer trades the deadlock for holding the whole stream in memory
and sending none of it until the work is done, which is not a stream.

`sse/stream` takes the request so it can bind `*req*` and `*current-user*` inside
the thread it starts. A new thread inherits no dynamic bindings, and producer
code reads those vars the same way view code does.

Any channel still satisfies `StreamSource`. A handler with a producer of its own
passes that channel straight through, and owns the thread and the close itself.

## Considered options

- **A push-style callback**, `(fn [emitter] (sse/send! emitter ...))`. Tram runs
  it on a virtual thread. No new dependency, and blocking I/O inside it is
  ordinary blocking I/O. Rejected in favour of the channel, which composes with
  producers an application already has and gives a bounded buffer for free. The
  emitter survives as the write side of the transport, below the protocol.

- **The handler calls http-kit directly** and returns its channel as `:body`.
  Rejected. Every application would write SSE framing by hand, handlers would
  stop being testable without a server, and it welds the handler layer to
  http-kit. The `:body` escape hatch stays available for anyone who wants exactly
  this.

## Consequences

- **Streaming apps take on core.async.** This is the cost the channel buys its
  ergonomics with, and it is worth naming plainly. An application that streams
  cannot opt out of the dependency the way it could with a callback.

- **A `go` block that blocks starves the dispatch pool.** core.async's `go`
  threads are a small fixed pool, and a `>!` is not the problem. Blocking I/O
  inside a `go` is. Producers that do real work use `async/thread`, and the docs
  say so at every example rather than in a footnote. Tram's own drain loop runs
  on a virtual thread and uses `<!!`, so it never touches the pool.

- **The protocol keeps the dependency at the edge.** `tram.sse` defines
  `StreamSource` with one method, `drain!`. The channel implementation lives in
  its own namespace. Two implementations exist on day one, a channel and a seq,
  which is what makes the seam real rather than hypothetical per the structure
  rules in `AGENTS.md`.

- **Rendering still belongs to Tram.** A source yields command maps whose
  `:dom/content` may be hiccup. The transport runs each one through the render
  concern-group, so a handler never builds html and never builds an SSE frame.
  ADR-0012 decides how.

- **The response schema grows a fourth mutually exclusive key.**
  `owns-body?` in `tram.rendering.template-renderer` becomes true for `:stream`
  too, so page wrapping and template rendering step aside with no other change.
