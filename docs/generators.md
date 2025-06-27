# CLI Generators

Tram provides some generators from the cli. Generators in Tram differ from
generators in most other frameworks because they do not directly generate the
requested code. 

Instead Tram generators create **runtimes**. A runtime is a namespace that isn't
in source control that lives at `/src/main/dev/runtimes`.

These runtimes contain two things: a **blueprint** data structure representing
the changes to make in your project, and a comment block with an invocation of a
tram function that will write those changes to your project.  The tram function
to write changes will overwrite previous changes.

The rationale for this workflow is that it can be hard to get the CLI command
correct in one go.  Rather than rewriting it, you can modify the blueprint,
which is more verbose and easier to understand and then modify those changes
without typing everything again.

TODO: add a flag to generators for "immediate mode" that will immediately write
the changes. enable by default?

## Migration generators 

These generators can be invoked to create runtimesfor data migrations. The CLI
syntax looks like this:

`tram generate:model <attributes>`

The attribute syntax looks like this:

`[modifiers][column-name]:[column-type]=[default value]`
    
The only required field is `column-name`, which will default to `TEXT`
    
### Modifiers
    
    The two supported modifiers are
* `!` for required fields
* `^` for unique fields

### Column Type
Postgres column types are supported, see
`tram.generators.blueprint/PostgresType` for a full list. If omitted this will
default to `:text`. 

### Default Value

Default values can either be literals (which will be parsed from strings into
the matching data type from `column-type`) or they can be postgres functions.
Function default values are indicated with the prefix `fn/`, eg.
`created-at:timestamptz=fn/now`.

### References (foreign keys)

Foreign key references do not follow this same syntax, they are declared by
writing `references(table-name)`.  This will automatically be converted into a
singularized version of the table name apended with `-id`.  Nothing else is
required for you to write these.  

### Examples

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

