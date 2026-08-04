# Tram

Tram is an opinionated Clojure web framework. This glossary pins the vocabulary
Tram uses for its request-handling pipeline so extensions, generators, and
architecture reviews name the same concepts the same way.

## Language

### Request pipeline

**Concern-group**:
A named, bidirectional cluster of interceptors that together handle one concern
of the request lifecycle. An application composes a handful of concern-groups
into its interceptor chain rather than listing individual interceptors.
_Avoid_: middleware stack, interceptor bundle, compound interceptor

**wire-format**:
The concern-group that interprets an HTTP request as Clojure data — parameter
parsing, multipart, and coercion. The `format` transport codec (content
negotiation and response encoding) sits outermost, outside the error boundary,
so it also encodes error responses; it is therefore not part of this group.
_Avoid_: parsing, serialization, formatting (each names only one direction)

**security**:
The concern-group enforcing request-safety policy — CSRF today, request-safety
concerns such as security headers later.
_Avoid_: auth (authentication is application-owned and sits outside this group)

**render**:
The concern-group turning a handler's response into a response body — view
resolution, layout and page wrapping, and route-reference expansion.
_Avoid_: view, output

**must-run-after**:
An ordering dependency declared on an interceptor, naming the interceptors that
must be present and earlier in the chain for it to work. Its absence means the
interceptor has no ordering requirement.
_Avoid_: priority, weight, ordinal

### Rendering

**handler**:
A fn from a request to a response. It supplies the locals its view renders with.
It may also name a view other than the one its route declares.
_Avoid_: controller, action, endpoint

**view**:
A fn from locals to hiccup. It produces the content of a response. A view lives
in a `*.views.*` namespace and is named by a `:view/` keyword. A route may name a
view where it would name a handler; Tram then supplies the handler.
_Avoid_: template — Selmer owns that word here, and the CLI uses it for the
new-app scaffold. Also avoid partial, component.

**locals**:
The map a view is called with. A handler supplies it on its response. A view a
route names directly has no handler to supply them, so it receives an empty map.
Locals are the only per-request data a view takes as an argument; it reads
everything else from dynamic vars.
_Avoid_: props, assigns, model, context

**layout**:
A fn from hiccup to hiccup, declared on a route and applied around its view's
output. Layouts stack: each layout on the path down the route tree wraps the ones
below it. A layout is named by a `:view/` keyword and lives alongside views, but
it is not a view: its argument is the hiccup it wraps, not locals.
_Avoid_: wrapper, chrome, page — a page is a kind of response, not a wrapper

**page**:
A response that carries a complete html document — html, head, meta, and body.
It replaces the whole document. An application supplies one fn to build a page
and gives it to the render concern-group.
_Avoid_: full page, document, shell, root, page-wrapper, full-page-renderer

**partial**:
A response that carries html fragments instead of a document. Each top-level
element replaces the element on screen with the same id, so one partial can
update several targets at once. A partial gets no layout and no page around it,
because both are already on screen.
_Avoid_: fragment, snippet, chunk, swap

Page and partial are the two kinds of response a view can produce, and they
differ only in that rule: a page gets its layouts and its page fn, a partial gets
neither. The rhizome runtime asks for partials; a browser page load asks for a
page.

### Rhizome

**directive**:
The edn value in one of an element's `::on/*` attributes. It holds the commands
that run when that trigger fires, and the modifiers that govern when they run. An
element holds one directive per trigger and as many triggers as it needs.
_Avoid_: command (that names a verb inside a directive), binding, action,
handler, rhizome-on

**trigger**:
The event that runs a directive, named by the directive's own attribute.
`::on/click` runs on a click. Rhizome never infers a trigger from an element's
tag. Besides dom events, rhizome ships the synthetic trigger `::on/mount`.
_Avoid_: event. The dom event is the object the browser hands a command. The
trigger is which one rhizome listens for.

**modifier**:
A key on a directive that governs when or whether its commands run, such as
`:debounce` or `:once`. A modifier never changes what a command does.
_Avoid_: filter, wrapper, option

**command**:
The verb a directive names, under its `:command` key. A command is a namespaced
keyword such as `:http/post` or `:dom/morph`. Rhizome ships a set of commands and
an application registers its own.
_Avoid_: op, operation, effect, action

**command definition**:
The map that registers one command. It carries the command's key, its lifecycle
fns, and the schema its directive must satisfy.
_Avoid_: handler, method, descriptor, spec (a spec here is a document in
`docs/specs/`)

**registry**:
The map from command keyword to command definition. Rhizome ships a default
registry and an application extends it before start.
_Avoid_: config (the registry is one key inside the config), table, dispatch map

**config**:
The value rhizome starts with. It holds the registries and the error sink. An
application builds one and hands it to `start!`.
_Avoid_: options, settings, opts

**signal**:
A named piece of reactive client state. Commands read and write signals, bindings
derive the dom from them, and every request carries them to the server.
_Avoid_: state, store, variable, atom

**binding**:
An attribute that derives part of an element from signals, such as `::rz/text` or
`::rz/class`. Rhizome re-applies a binding whenever a signal it read changes. A
binding is the only way rhizome changes an element's own appearance.
_Avoid_: reaction, watcher, computed, effect, subscription

**mount**:
To attach a directive to its element. Rhizome mounts every directive in the
document at start, and mounts the directives in any subtree a morph adds.
_Avoid_: wire, bind, hydrate, register, initialize

**unmount**:
To detach a directive from its element and release what the directive held.
Rhizome unmounts a directive when its element leaves the page.
_Avoid_: teardown, cleanup, destroy, dispose

### Streaming

**stream**:
A response whose body is a sequence of stream events over one open connection,
sent as `text/event-stream`. It is the third kind of response a handler can
produce, beside a page and a partial. Any endpoint may return one.
_Avoid_: sse (name the thing, not the acronym), feed, socket, subscription,
channel (a channel is one way an application produces a stream's events)

**stream event**:
One frame on a stream. It names a command and carries that command's arguments
as edn. The two-word term is deliberate: **event** on its own already means the
dom event object the browser hands a command.
_Avoid_: message, chunk, frame, patch, notification

**stream source**:
What a handler returns under `:stream`. It yields the stream's events in order.
Tram accepts a core.async channel or a seq, and an application adds its own by
satisfying `tram.sse/StreamSource`.
_Avoid_: producer, publisher, generator, feed

**emitter**:
The write side of an open stream. It renders an event, frames it, and puts it on
the wire, and it reports when the client has gone. One implementation per http
server.
_Avoid_: sink (rhizome's error sink already owns that word), writer, connection
