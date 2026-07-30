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
