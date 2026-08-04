# 03, server sent events

## Status

drafted, not implemented. Decided by ADR-0009, ADR-0010, and ADR-0011.

## Summary

How a `text/event-stream` response is read, how each frame becomes a command, and
when the stream closes.

The server half is `docs/specs/tram/streaming-responses.md`.

## Prerequisites

Two pieces of earlier milestones have to be real first. Neither is optional and
neither is small, so they are named here rather than discovered during the work.

**Unmount, from milestone 1.** `mount.cljs` today watches `addedNodes` and
ignores `removedNodes`. Nothing tears down and no command definition has an
`:on-unmount`. A stream is the first thing rhizome holds that must be released,
so unmount stops being a design nicety and becomes the thing that keeps a page
from leaking connections until it hits the browser's per-host cap.

**Content-type dispatch, from milestone 2.** `execute-http` in
`directives.cljs:22` calls `.text` on every response and morphs whatever comes
back. There is nowhere to put a stream branch until the dispatch exists.

## The stream is not a command

Nothing new appears in the registry. `:http/get` and its four siblings gain a
branch, and that is the whole of the addition.

```
content-type            branch
────────────────────────────────────────────────
text/html               read as text, run :dom/morph      (milestone 2)
text/event-stream       read as a stream, run each frame  (this one)
application/edn         signal patch                      (milestone 7)
anything else           :on-error, change nothing
```

Any endpoint can stream because the response decides, not the element. The same
`{:command :http/post :http/url "/import"}` gets html today and a stream when the
handler changes, and the page is not edited.

## Reading the stream

```clojure
(let [reader  (.getReader (.-body response))
      decoder (js/TextDecoder. "utf-8")]
  ...)
```

One reader, one loop, one leftover string. No core.async. Ordering is the
reader's, and framing is a string-splitting problem rather than a concurrency
one. The old runtime required `cljs.core.async` and never used it, which the
README already lists as a mistake not to repeat.

The loop, per chunk:

1. `(.decode decoder value #js {:stream true})`, so a multi-byte character split
   across two chunks survives.
2. Append to the leftover.
3. Split on a blank line, `#"\r?\n\r?\n"`. Everything before the last separator
   is complete frames. What follows it is the new leftover.
4. Parse and dispatch each complete frame.

Chunk boundaries and frame boundaries have nothing to do with each other. One
chunk may hold six frames or a third of one.

### Parsing a frame

A frame is lines of `field: value`, with one optional space after the colon.

| Line              | Handling                                        |
|-------------------|--------------------------------------------------|
| `event: <name>`   | The command name. Last one wins.                 |
| `data: <text>`    | Collected. Several are joined with `\n`.         |
| `: <text>`        | A comment. Discarded. Keep-alives are these.     |
| `id:`, `retry:`   | Read and ignored. Nothing reconnects.            |
| anything else     | Ignored, per the format.                         |

A frame with no `data` line dispatches nothing, which is what makes a keep-alive
free.

The server always emits `data` on one line, because `pr-str` escapes newlines
inside strings. The client joins multiple anyway. A parser that assumes its own
server is on the other end is a parser that breaks on the first proxy that
rewrites something.

### Dispatching a frame

```clojure
(rz/run! ctx (assoc (edn/read-string data) :command (keyword event-name)))
```

That is the whole of it. ADR-0010 makes the event name a command keyword, so a
frame is a command invocation and `run!` is the same seam every other command
goes through. An application that registered `:app/confetti` can have its server
fire it with no client change.

`event: dom/morph` therefore lands on exactly the morph of milestone 2, id
matching and all. A stream patching four ids sends four frames or one frame with
four elements, and both work.

### Frame errors

Each goes to `:on-error` and drops one frame. The stream keeps reading.

| Failure                          | Why it is not fatal                              |
|----------------------------------|--------------------------------------------------|
| `data` is not readable edn       | One bad frame is not a broken connection.        |
| The command is not registered    | The server named something this page lacks.      |
| The command fails its schema     | Same reason milestone 1 checks schemas at all.   |
| No `event` field                 | Nothing to dispatch on. The default name `message` names no command. |

This matches milestone 2, where one bad element in a fragment does not stop the
rest landing.

## Lifecycle

`:on-trigger` returns the abort controller as mount state, exactly as a
non-streaming request does. `:on-unmount` aborts it, which cancels the fetch and
tears down the reader.

```clojure
{:key        :http/post
 :on-trigger (fn [ctx] (let [ac (js/AbortController.)] (start! ctx ac) ac))
 :on-unmount (fn [{:keys [mount-state]}] (.abort mount-state))}
```

Nothing about this is specific to streaming. It is the milestone 2 contract, and
a stream is the case that makes it load-bearing rather than tidy. A stream that
outlives its element goes on morphing ids that a later page reused.

**A new request on an element replaces that element's open stream.** Two clicks
on a streaming button would otherwise leave two streams patching the same ids in
whatever order they arrive. Opening a stream aborts the element's previous one
first, per command key. This sharpens milestone 2's open question about
concurrent requests: for a long-lived stream, cancel is the only answer that is
not surprising, so streams take it now and short requests keep the question.

The abort is expected, so it does not reach `:on-error`. A reader rejecting with
an `AbortError` after its controller fired is the design working.

## What this milestone does not do

- **Reconnect.** `fetch` does not retry and rhizome adds none. ADR-0009 records
  why, and it needs a server that can replay before it needs a client that asks.
- **Send anything up the stream.** It is a response body. Talking back is another
  request.
- **Backpressure.** The reader pulls as fast as the network gives, and there is
  no way to ask for less.
- **Signal events.** `:signal/set` over a stream needs no work here. It is one
  more command name once milestone 4 registers it.

## Testing

The parser takes a string and returns frames, so most of this is tested with no
network at all: split frames across chunk boundaries, a multi-byte character cut
in half, a comment-only frame, `\r\n` line endings, a frame with three `data`
lines.

`test/main/rhizome/fake_server.cljs` already exists for the browser tests and
gains a streaming route, which is where abort-on-unmount is checked: mount an
element, open a stream, remove the element, assert the server saw the connection
drop.

## Open questions

- **Does an element need to know a stream is open?** A spinner wants it. Milestone
  6 gives bindings, and an `in-flight?` signal would answer for streams and plain
  requests together. That argues for waiting rather than adding a stream-only
  mechanism now.

- **Should `run!` from a frame get a different ctx than from an event?** The ctx
  carries `:event`, the dom event, which is nil for a frame. `::on/mount` already
  has the same hole, so the answer is probably no, but a command that reads
  `:event` without checking will now fail in a second way.

- **What happens to a stream when its element is morphed rather than removed?**
  Idiomorph keeps an element it can match and rewrites its attributes, so the
  element never unmounts and the stream lives on. That is right when the server
  is patching the streaming element itself, and wrong if the intent was to stop.
  Milestone 2's morph rules decide this and this spec inherits whatever they say.
