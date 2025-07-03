# Handlers

Request handlers receive a map representing an http request, and return a map
representing http request.

In Ring, the default behavior is to use `:body` for the body of the response.
That works perfectly well, but Tram has a few alternate options to help you move
faster.

Instead of `:body`, you can use the keys `:template` and `:context`, where
context is a map of data to send to the view function, and template is a var for
that view function.

Even easier, if you don't pass a `:template`, the template is automatically
resolved to a function with the same name in the corresponding view namespace.

For example, if your handler is
`sample.handlers.user-handlers/get-user-handler`, the template value will
automatically resolve to `sample.views.user-views/get-user`.  See the following
table for a few examples of how this resolution works.

| handler                                        | auto resolved view-fn            |
|------------------------------------------------|----------------------------------|
| sample.handlers.user-handlers/get-user-handler | sample.views.user-views/get-user |
| sample.handlers.user-handlers/get-user         | sample.views.user-views/get-user |


When it comes to `:context`, you need less than you'd expect, because Tram
automatically binds `*req*` `*res*` and `*current-user*`.  If you pass no
context, it defaults to an empty map.
