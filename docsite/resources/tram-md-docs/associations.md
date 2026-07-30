# Associations

Associations are how Tram defines relationships between models to make it easy
to get more data without writing SQL. Associations are intentionally not full
featured. Nontrivial use cases are better suited to raw SQL via queries
assembled with HoneySQL.

Associations are used in 2 steps.

1. Defining the associations in model files
2. Hydrating an instance with data

Tram only supports 3 explicit kinds of associations.

- has-one (implicit)
- has-many (call `tram.db/has-many!`)
- belongs-to (call `tram.db/belongs-to!`)

**Hydration** is when a model has additional data assoc'd into it that are
held in other tables.

If a model has a foreign key on it, that model can be hydrated without
configuration.

Hydrating models with a belongs-to or has-many relationship requires
configuration. Define an association with either `tram.db/belongs-to!` or
`tram.db/has-many!` and you can use `tram.db/hydrate` to fetch those data.

::: info
Under the hood, Tram uses Toucan2 for hydration.  You can read more on
their Github.
:::

## Belongs To

Authors belong to books.  As an example consider these connections:

```clojure
(def author
  {:id 2
   :name "Kurt Vonnegut"})

(def book
  {:id 1
   :title "Slaughterhouse Five"
   :author-id 2})
```

This relationship is so universal, that writing out the sql for every instance
feels wasteful.  Tram makes this easy -- there is nothing to write.  In this
example, it can be automatically determined that books have authors, and the ids
are inferred so long as the tables use conventional names.

```clojure
(db/hydrate book :author)  ;; Book should be a db record, not a map.

;; =>
{:id 1
 :title "Slaughterhouse Five"
 :author-id 2
 :author {:name "Kurt Vonnegut"
          :id 2}}
```

## Has One

A has-one relationship is the inverse of a belongs-to relationship.  In this
example, a supplier has only one account.

```clojure
(def supplier {:id 1
               :name "Acme Industrial Supplier"})

(def account {:id 14
              :supplier-id 1
              :account-number "123123123"})

;; models/suppliers.clj
(db/has-one! :models/suppliers :account)

;; In your application

(db/hydrate supplier :account)
;; =>
{:id 1
 :name "Acme Industrial Supplier"
 :account {:id 14
           :supplier-id 1
           :account-number "123123123"}}
```
 ## `has-many`/`belongs-to`

I would also like to be able to hydrate books on authors.  That association is
also easy.

```clojure
;; In models/authors.clj
(db/has-many! :models/authors :books)

;; In your application
(db/hydrate author :books)
;; =>
{:id 2
 :name "Kurt Vonnegut"
 :books [{:id 1
           :title "Slaughterhouse Five"
           :author-id 2}
          {:id 2
           :title "Breakfast of Champions"
           :author-id 2}]}
```

::: tip
 Fields are not altered during hydration. You can control selection with
 `deftransforms` and `after-select`.
 :::

## `has-many`/`has-many`

Sometimes there are many records on both sides of the relationship.

Tram supports a many-to-many relationship via conventionally named join table
without any modifications to the `has-many`/`belongs-to` association definition.

For example


| `assemblies` |             |
|--------------|-------------|
| id           | primary-key |
| name         | string      |

| `parts`       |             |
|-------------|-------------|
| id          | primary-key |
| part_number | string      |


| `assemblies_parts` |                               |
|--------------------|-------------------------------|
| id                 | primary-key                   |
| assembly_id        | foreign key to assemblies(id) |
| part_id            | foreign key to parts(id)      |


::: info
The convention for join tables is something like
`(->> [table-1 table-2] sort (str/join "_"))`

You can read the source for `tram.language/join-table` to get a full picture.
:::

```clj
;; in models/assembly.clj
(tram.associations/has-many! :models/assemblies :parts)

;; in models/part.clj
(tram.associations/has-many! :models/parts :assemblies)
```

Since the join table uses the Tram conventional name, there is no configuration
required.

If, however, you want to use the join table as a model in its own right you'll
need to do more.

| `physicians` |             |
|--------------|-------------|
| id           | primary-key |
| name         | string      |

| `patients` |             |
|------------|-------------|
| id         | primary-key |
| name       | string      |


| `appointments`   |                               |
|------------------|-------------------------------|
| id               | primary-key                   |
| physician_id     | foreign key to physicians(id) |
| patient_id       | foreign key to patients(id)   |
| appointment_time | datetime                      |

In this situation, you'd want to use `:models/appointments` as a real model.
Everything about that model works exactly the same as any other model (querying,
etc.  The model also automatically has `belongs-to` associations for
`:physician` and `:patient`).

```clojure
;; in models/physician.clj
(db/has-many! :models/physicians :patients {:join-table :appointments})
(db/has-many! :models/physicians :appointments)

;; in models/patient.clj
(db/has-many! :models/patients :physicians {:join-table :appointments})
(db/has-many! :models/patients :appointments)

;; In your application
(db/hydrate patient :physicians)
(db/hydrate physician :patients)
```

