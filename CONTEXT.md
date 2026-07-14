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
The concern-group translating between the bytes on the HTTP wire and Clojure
data — content negotiation, body decoding, parameter parsing, coercion, and
response encoding.
_Avoid_: parsing, serialization, formatting (each names only one direction)

**security**:
The concern-group enforcing request-safety policy — CSRF today, request-safety
concerns such as security headers later.
_Avoid_: auth (authentication is application-owned and sits outside this group)

**render**:
The concern-group turning a handler's returned template or hiccup into a
response body — view resolution, layout wrapping, and route-reference expansion.
_Avoid_: view, output

**must-run-after**:
An ordering dependency declared on an interceptor, naming the interceptors that
must be present and earlier in the chain for it to work. Its absence means the
interceptor has no ordering requirement.
_Avoid_: priority, weight, ordinal
