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
This directory holds http handlers and partial routes. They should be named
singularly, `thing_handler.clj`. They should export a variable called `routes`
which should be embedded in the router in `routes.clj`.

Read more about routes in the [routing guide](./routing-guide.md).

Route handlers live here.

Route handlers are functions that take `req`, an http request, and return a map
 representing an http http response. Typically you'll only need to set `:status`
 and `:headers`.

By default, the response body will be the value of the function with the same
name in the corresponding views namespace. For example
`com.my-app.handlers.dashboard-handlers/get-data` will use the function
`com.my-app.views.dashboard-views/get-data` as the template fn (sometimes called
view fn).

## `src/sample_app/models`

Directory for models. Read more about model files in the
[model-guide](./model-guide.md).

## `src/sample_app/views`

Directory for views. Contains files that export view functions for their
corresponding handlers. These are likely specific to the view in question and
not reusable. If this file grows too large, these can be nested.

Read more about views in the [views guide](./views-guide.md).

## `src/sample_app/concerns`

Directory for concerns. Your business logic should live here. Read more about
concerns in the [concern guide](./concern-guide.md).

## `src/sample_app/components`

View components are functions that return hiccup (html) but that you want to
reuse. Some examples might be `button` or `callout`.

Anything that doesn't take a `ctx` context arg is a good candidate to go in
components.

Read more in the [components guide](components-guide.md).

## `src/sample_app/config.clj`

System configuration file. This is where you'd write any configuration for new
pieces of your system. This is for things like connections, that need to be
managed via a lifecycle. Database connections are a good candidate. External api
wrappers; redis connections; anything like that where there's some configuration
and buildup and teardown required. They're configured via Integrant.

## `src/sample_app/core.clj`

Root to your application. This is mostly for setting up the server and booting
up the application.

## `src/sample_app/db.clj`

Database configuration. There's some default configuration for postgres in here.
Things like how to interpret data types, any connection configuration,
database-wide changes via Toucan.

## `src/sample_app/routes.clj`

Root for routes and the router.

You will add new routes to your application here. 

## `src/sample_app/server.clj`
Root for the http server.

## `bin`

For binary files. The test runner binary lives here, and any other binary files
your appplication needs can be stored in this directory.

## `build.clj`

A default Clojure build file for your project.  Starts out building a simple jar
file, but you can do anything you need here to build your application.

## `deps.edn`

Root Clojure configuration file.  Think of a package.json or Gemfile.

## `dev`

This directory, which is only added to the classpath with the `:dev` alias, is
used to contain any devtime related code that you won't need in production.

An example is `migrations.clj`, which is a file for interacting with migrations
at the REPL.

This directory also stores runtimes, which are ignored by default. Read more
about those in the [runtimes guide](./runtimes.md).

## e2e

Holds a npm project that can be used to test e2e with Playwright. For now, this
is the recommended approach.  There is some scaffolding there to make it easy to
get started.

## `mise.toml`

Environment, tool, and task configuration.

Read their [official docs](https://mise.jdx.dev/) for more information.

## `resources`

This holds non source code requirements for your application.  These are
included on the classpath by default.

Included are an images directory (not images served on your server); a
migrations dir for sql migrations; a public dir for assets accessible over http;
a seeds dir, for Clojure code that initializes seed data; and a tailwindcss
project for generating index.css from tailwind classes that you use in your
views.

## `test`
Directory for tests.
