# CLI Generators

Tram provides some generators from the cli. Generators in Tram differ from
generators in most other frameworks because they do not directly generate the
requested code. 

Instead Tram generators create runtimes.  Those runtimes are written to
`/src/main/dev/runtimes*`.  They contain a **blueprint**, data that represents
the code generation, and invocations for the code that will generate the code
based on that blueprint. 

The rationale for this is that it is that it's hard to know exactly what you
want from the cli and type it correctly in an abbreviated syntax on the fly.
The more verbose blueprint representation gives you an extra moment to modify
your generated code in an easier way without retyping it into a terminal.  

TODO: add a flag to generators for "immediate mode" that will immediately output
the code.

## Migration generators 

These generators can be invoked to create runtimes that contain the code for
data migrations.  The CLI syntax works like this:

[modifiers][column-name]:[column-type]=[default value]
    
The only required field is `column-name`, which will default to type `text`
    
The two supported modifiers are
* `!` for required fields
* `^` for unique fields

Postgres column types are supported, see
`tram.generators.blueprint/PostgresType` for supported column types.  If omitted
this will default to `:text`.

Default values can be provided, and to use a builtin function, prefix the name
with `fn`, eg. `created-at:timestamptz=fn/now`.

Here are some examples of attributes and how they are parsed:

```clojure
"first-name"
;; =>
{:name "first_name"
 :type :text}

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

"!^email:citext"
{:name      "email"
 :type      :citext
 :required? true
 :unique?   true}

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

"!created-at:timestamptz=fn/now"
;; =>
{:name      "created_at"
 :type      :timestamptz
 :required? true
 :default   :fn/now}

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

"references(teams)"
;; =>
{:name      "team-id"
 :type      :integer
 :required? true}

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

"subscribed=yes"
;; =>
{:name    "subscribed"
 :type    :text
 :default "yes"}
```

