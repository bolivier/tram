# Streams ride fetch, not EventSource

## Status

accepted

## Decision

Rhizome consumes `text/event-stream` by reading the body of an ordinary `fetch`.
It does not use `EventSource`.

A stream is therefore not a separate kind of request. Any command that makes a
request can get a stream back, because the decision is the response's
`content-type` and nothing else. `:http/post` to `/import` returns html today and
a stream tomorrow with no change on the element.

## Considered options

- **`EventSource`.** The browser's own client. Rejected on four counts, each of
  which alone is disqualifying.

  | Limit              | Why it disqualifies                                                                      |
  |--------------------|------------------------------------------------------------------------------------------|
  | GET only           | A stream that reports on work cannot start that work.                                    |
  | No request headers | It cannot send `rhizome-request: true`, the whole contract with the render group.        |
  | No request body    | ADR-0007 makes the body the signal map. An `EventSource` carries no signals.             |
  | Reconnects, always | It retries on every close, including the deliberate ones, and that cannot be turned off. |

  Working around any of these means query-string encoding the signal map and a
  server that treats stream requests as their own species. That is a second
  request path beside the one rhizome already has.

- **WebSocket.** Bidirectional, one connection for everything. Rejected for now.
  Rhizome's model is request in, patches out, and a socket is a worse fit for that
  than a response body is. It also needs connection state, a reconnect policy, and
  a message router before it does anything a stream does not. Revisit when
  something needs server-initiated messages with no request behind them.

## Consequences

- **The stream is tied to the element that opened it.** It is the response to that
  element's request, so it aborts with that request's `AbortController` when the
  element unmounts. `EventSource` has no such tie and would leak past a morph.

- **Nothing reconnects.** `fetch` does not retry, and rhizome adds no retry policy.
  A dropped stream is a dropped stream until something re-triggers the command.
  This is the honest state of it: retry needs a `Last-Event-ID` and a server that
  can replay, and neither exists. Milestone 2 already deferred retry for the same
  reason.

- **HTTP/1.1 caps concurrent streams at six per host.** Every open stream holds a
  connection. Six streaming elements on one page starve every other request on
  that origin, including the next navigation. HTTP/2 raises the cap far past what
  a page will use. Development over plain HTTP/1.1 is where this bites, so the
  docsite example uses one stream, and a page wanting many wants one stream
  patching many ids instead.

- **Rhizome owns the frame parsing.** `EventSource` would have done it. This is
  roughly forty lines: decode chunks, hold a leftover string, split on a blank
  line, read `field: value`. It is specified in
  `docs/specs/rhizome/03-server-sent-events.md` and it is testable without a
  network, which `EventSource` is not.
