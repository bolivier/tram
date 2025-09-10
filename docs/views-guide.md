# Views

Views are functions that return vectors. Those vectors are rendered as html
before the response is sent.

## Context

Views rarely exist on their own without any values bound. To support usage, Tram
requires a view to take a single argument, `ctx` or `context` which will be
called "context" going forward. Context is a map of values that you set from
your handler fn. The value is set with the key `:context` in the response.

Dynamic variables `*req*` `*res*` and `*current-user*` are also available in all
view functions.
