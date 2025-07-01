# Tram Application Structure

Tram has a handful of expected patterns for file structure.  

```
tram-starter-template
├── bin
│   └── kaocha
├── build.clj
├── deps.edn
├── dev
│   ├── migrations.clj
│   ├── runtimes
│   └── user.clj
├── e2e
│   ├── fixtures.ts
│   ├── pages
│   │   └── Page.ts
│   └── tests
├── mise.toml
├── resources
│   ├── document-storage
│   ├── images
│   ├── migrations
│   │   └── init.sql
│   ├── public
│   ├── seeds
│   │   └── init.clj
│   └── tailwindcss
├── src
│   └── sample_app
│       ├── components
│       ├── concerns
│       │   └── http.clj
│       ├── config.clj
│       ├── core.clj
│       ├── db.clj
│       ├── handlers
│       ├── models
│       ├── routes.clj
│       ├── server.clj
│       └── views
├── test
│   └── sample-app
├── tests.edn
└── tram.edn
```

Let's go over some of these in more detail now. 


## `src/sample_app/handlers`
This file holds http handlers and route partials. They should be named
`thing_handler.clj`. They should export a variable called `route` which can be
embedded in the router in `routes.clj`. 

Read more about routes in the [routing guide](./routing-guide.md).

You'll also write route handlers here.  

Route handlers take a single argument, `req`, a map representing the http
request, and return a map representing an http response. Typically you'll only
set `:status` `:headers` and `:body`. 

`req` has a lot of injected values for ease of access and ease of testing. New
values can be added to a request by adding an injector to your router instance.
Injectors are typically kept in `/concerns` and are implemented as interceptors. 

> [!IMPORTANT]  
> `:body` is, by default, interpreted as a hiccup vector to be transformed into
> html.  To skip that transformation, set `[:headers "content-type"]` to
> something other than `"text/html"` in the response. 


## `src/sample_app/models`

Directory for models. Read more about model files in the
[model-guide](./model-guide.md).

## `src/sample_app/concerns`

Directory for concerns. Read more about concerns in the [concern
guide](./concern-guide.md).

## `src/sample_app/views`

Directory for views.  Contains files that export view functions for their
handlers.  These are likely specific to the view in question and not reusable.
If this file grows too large, these can be nested. 

## `src/sample_app/components`

A place for your reusable view components.  Tram encourages reusble components
for view elements, much like React, but they are simple functions and interact
via htmx.  These can be pre-styled components that render a particular way in
your application.

Read more in the [components guide](components-guide.md).

## `src/sample_app/config.clj`

System configuration file. This is where you'd write any configuration for new
pieces of your system. This is for things like connections, that need to be
managed via a lifecycle. Database connections are a good candidate. External api
wrappers; redis connections; anything like that where there's some configuration
and buildup and teardown required. They're configured via Integrant.

## `src/sample_app/core.clj`
Root to your application. 

## `src/sample_app/db.clj`
Database configuration. There's some default configuration for postgres in here.
Things like how to interpret data types, any connection configuration, Database
wide changes via Toucan.

## `src/sample_app/routes.clj`
Root for routes and the router.

This is the root of your router.  You can either add new routes inline here,
or reference variables from your handler files.

## `src/sample_app/server.clj`
Root for the http server.

## `bin`

For binary files. The test runner binary lives here, and any other binary files
your appplication needs can be stored in this directory.

## `build.clj`

A default Clojure build file for your project.  Starts out building a simple jar
file, but you can do anything you need here to build your application. 

## `deps.edn`
A configuration file.  Think of a package.json or Gemfile.

## `dev`

This file, which is only added to the classpath with the `:dev` alias, is used
to contain any devtime related code that you won't need in production.  

An example is `migrations.clj`, which is a file for interacting with migrtaions
at the REPL.

This directory also stores your runtimes, which are ignored by default.  Read
more about those [here.](./runtimes.md)

## e2e

Holds a npm project that can be used to test e2e with Playwright. For now, this
is the recommended approach.  There is some scaffolding there to make it easy to
get started.

## `mise.toml`

Environment configuration.

## `resources`

This holds non source code requirements for your application.  These are
included on the classpath by default.

Included are a local document storage dir (think a local S3); an images file
(not for serving over http); a migrations dir for sql migrations; a public dir
for accessible assets over http; a seeds dir, for Clojure code that initializes
seed data; and a tailwindcss project for generating index.css from tailwind
classes that you use.

## `test`
Directory for tests. 
