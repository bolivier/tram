# 02, the wire protocol

## Status

drafted, not implemented. Decided by ADR-0002, ADR-0003, and ADR-0007.

## Summary

The five http commands, what they send, what they accept back, and how a returned
html fragment lands on the page.

## The http commands

```clojure
{:command  :http/post
 :http/url "/counter"}
```

One command per verb: `:http/get`, `:http/post`, `:http/put`, `:http/patch`,
`:http/delete`. `:http/url` is the only argument in this milestone.

Every request carries `rhizome-request: true`, which is what
`tram.impl.http/rhizome-request?` reads to decide a response is a partial rather
than a page. That header is the whole contract between the runtime and the render
concern-group, and it does not change.

**A non-GET request carries the closest enclosing form as its body.** Fields go
as `application/edn`. A form holding a selected file goes as
`multipart/form-data` instead, because a `File` has no edn representation: the
fields ride in one part named `rhizome-params`, still edn, and each file gets
its own part.

`tram.wire-format/rhizome-multipart-interceptor` reverses that split, so
`:body-params` looks the same either way and a handler reads
`[:parameters :body]` without caring which transport ran. Files land there as
`tram.wire-format/File`, whose `:tempfile` ring deletes once the response
finishes.

A multipart request without a `rhizome-params` part is left alone, so a plain
HTML form upload still reaches `[:parameters :multipart]`.

An in-flight request aborts when its element unmounts. `:on-trigger` returns the
abort controller and `:on-unmount` calls it, which is what those hooks exist for.

## The response

Dispatch on `content-type`.

| Content type        | Meaning                                    | Milestone |
|---------------------|--------------------------------------------|-----------|
| `text/html`         | Elements to morph                          | this one  |
| `application/edn`   | Signals to patch                           | 7         |
| `text/event-stream` | A stream of either                         | 3         |

An unrecognised content type goes to `:on-error` and changes nothing. The old
runtime fell through silently, so a json response did nothing and said nothing.

### Status codes

**A non-2xx response does not morph.** The old runtime morphed any body it got,
so a 500 error page replaced the element that asked for it. A failure goes to
`:on-error` with the status and the body.

A 204 succeeds and morphs nothing, which is how a command says "worked, page
unchanged".

Redirects are milestone 8. A 3xx reaching this code goes to `:on-error` for now,
rather than being followed into a full page load that discards the response.

## Morph

Per ADR-0003, id is the only targeting mechanism.

1. Parse the body as html. Take the element children of its body. Text and comment
   nodes at the top level are ignored.
2. For each element, in document order, read its `id`.
3. Find that id, searching from the root node of the element the directive sits
   on. A directive inside a shadow root finds ids in that root, not the document.
4. Morph with idiomorph, `morphStyle` `outerHTML`. The returned element replaces
   the page element whole, including its attributes.

Mounting the result needs no idiomorph callback. The `MutationObserver` from
milestone 1 sees every node idiomorph adds or removes. This is why the observer
went in first, and it settles the open question the old plan carried about which
callbacks fire on moved versus added nodes.

A fragment holding several top level elements updates several places at once. Each
is matched on its own, and one failing does not stop the others.

Morph is also a command in its own right, `:dom/morph` with `:dom/content`. The
http commands call it through `run!` rather than reaching into it.

### Morph errors

Both failures are the server's fault, and both report through `:on-error` naming
the id and the element's outer html:

- **A fragment element carries no id.** Rhizome cannot place it.
- **No element on the page has that id.** The target is gone, or the id is wrong.

Neither throws. One bad element must not stop the rest of the fragment landing.

## :dom/remove

```clojure
{:command :dom/remove
 :dom/id  "row-4"}
```

`:dom/id` is optional and defaults to the element the directive sits on. It is the
only command besides morph that names an element, because ADR-0007 replaced the
rest with bindings.

## The command set after this milestone

```
:http/get :http/post :http/put :http/patch :http/delete
:dom/morph
:dom/remove
```

Eight commands. `:signal/set` joins them at milestone 4.

## Out of scope

- Request bodies, which are the signal map. Milestone 7.
- `text/event-stream`. Milestone 3.
- Redirects and history. Milestone 8.
- Retry and backoff. Datastar has a retry policy on its backend actions. Rhizome
  does not need one before it has a stream to lose.

## Open questions

- **Does a failed request get a way to render something?** htmx has
  `hx-ext="response-targets"` for exactly this, and the starter template uses it
  today. Sending the failure to `:on-error` is right for a bug. It is not enough
  for a 422 carrying validation errors, which is an ordinary outcome the page
  should show. That case may want a 4xx with `text/html` to morph normally, making
  only 5xx an error. **Decide this before the starter template migrates**, at
  milestone 9, because its authentication flow depends on the answer.
- **Should concurrent requests from one element be serialised?** Two fast clicks
  produce two posts and two morphs, and they can land out of order. Datastar
  cancels the in-flight request by default. Rhizome could cancel, queue, or ignore.
- Does `:dom/remove` earn its place, or is it a morph the server should have sent?
