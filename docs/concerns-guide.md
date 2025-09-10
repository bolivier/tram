# Concerns

Concerns are Tram's way of organizing your business domain logic.

These are meant to be namespaces of functions that handle some element in a
domain your app deals with.  You may be tempted to avoid putting functions 
that touch the db here.  Ignore that impulse.  These are simply functions 
for your domain objects.

For example, `com.my-app.concerns.authentication` might have functions like
`get-password` or `allowed?`.

There are no specific recommendations for how to split these up.
