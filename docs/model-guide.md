## Models Guide

Models represent your database objects.  Unlike other ORM tools, you don't have
to have an application level representation of the database model.  The models
are read straight from the database.  It is encouraged to write a Malli schema
for your models, so you can get free validation and parsing.

Database access in Tram is handled with Toucan2, an excellent light ORM library
for Clojure. [Read more about Toucan2](https://github.com/camsaul/toucan2).

Models are maps, they do not have methods.

The model namespace is solely for functions and configuration related to the db
itself.  Do not put domain logic here.  For example, if you need to map a 
model key, put it here.  If you need to parse some db value, put that here.

## Naming Conventions
Models are keyed by default as a keyword namespaced with `"model"` and the
plural form of the noun. That's what's expected in the utility functions, and
that's how Tram maps between foreign keys and table names.

Examples:  
- `:models/users`
- `:models/people`
- `:models/databases`
- `:models/indices`

## Actions

> [!NOTE]
> Try to use the same language for models that you do for the database.  That
> creates a clear indication that something is related to the database rather than
> a simple map.

### Select
### Insert
### Update
### Delete
### Validations
### Associations


## Deriving Models

Sometimes it is useful to have a subset of models with a slightly different
name.  One use case is model scoping. 

Adding derived models with slightly different behavior can be done like this: 

```clojure
(derive :models/people.cool :models/people)

(t2/define-after-select :models/people.cool
  [person]
  (assoc person :cool-name (str "Cool " (:name person))))

(t2/select-one :models/people.cool 1)
;; =>
{:name       "Cam"
 :cool-name  "Cool Cam"
 :id         1
 :created-at #object[java.time.OffsetDateTime 0x2691c719 "2020-04-21T23:56Z"]}  
```

## Transforms

## Before/After insert

## Schema Conventions

By convention 
- the primary key is a serial called `id`
- foreign keys use the form `team-id`, `user-id`. Singular noun with `-id`
  suffix.
- `created-at` and `updated-at` are added by default.  `updated-at` is kept up
  to date with a database trigger that gets included on migrations by default.
  

## Overriding Conventions

### Different primary key name
### Different table name
