# /tram-model — Generate a Model Namespace

Generate a Tram model namespace with Toucan2 hooks and association declarations.

## Usage

```
/tram-model <model-keyword> [attributes...]
```

**Examples:**
```
/tram-model :models/posts title:string! body:text! author:references(users) published:boolean
/tram-model :models/users email:citext^! password:text! username:string^!
/tram-model :models/comments body:text! post:references(posts) author:references(users)
```

The model keyword should match the Toucan2 model key used in `db/select`, `db/insert-returning-instance!`, etc.

## What to Generate

A model namespace at `src/<app>/models/<singular>.clj` with:
1. Appropriate `define-before-insert` / `define-after-select` / `define-before-update` hooks
2. Association declarations (`belongs-to!`, `has-many!`, `has-one!`)
3. Any inferred patterns based on field names

---

## Hooks Reference

```clojure
;; Before insert — modify data before writing to DB
(db/define-before-insert :models/<name> [record] ...)

;; After select — transform data after reading from DB
(db/define-after-select :models/<name> [record] ...)

;; Before update — modify data before updating
(db/define-before-update :models/<name> [record] ...)

;; After update — transform after update
(db/define-after-update :models/<name> [record] ...)

;; Before delete — hook before delete
(db/define-before-delete :models/<name> [record] ...)

;; After insert — hook after insert
(db/define-after-insert :models/<name> [record] ...)
```

Each hook receives the record map and must return the (possibly modified) record map.

---

## Automatic Pattern Inference

Apply these patterns automatically based on field names and types:

### Password Fields (`:password`, `:password-hash`, `:hashed-password`)
```clojure
;; before-insert: hash the plain password
(db/define-before-insert :models/<name>
  [record]
  (update record :password hash-password))

;; after-select: strip the hash from query results
(db/define-after-select :models/<name>
  [record]
  (dissoc record :password))
```
Requires a `hash-password` function — note it in the output and suggest where to put it.

### Token/Secret Fields (`:token`, `:api-key`, `:secret`)
```clojure
;; after-select: exclude sensitive field
(db/define-after-select :models/<name>
  [record]
  (dissoc record :token))
```

### Slug Fields (`:slug`) when a `:title` or `:name` field is also present
```clojure
;; before-insert: auto-generate slug from title if not provided
(db/define-before-insert :models/<name>
  [record]
  (if (:slug record)
    record
    (assoc record :slug (slugify (:title record)))))
```
Note that the user needs to provide `slugify`.

### Timestamp Expiry (`:expires-at`)
```clojure
(require '[java-time.api :as jt])

(db/define-before-insert :models/<name>
  [record]
  (assoc record :expires-at
    (-> (jt/local-date-time)
        (jt/plus (jt/days 30)))))
```

---

## Associations Reference

```clojure
;; belongs-to: this model has a foreign key pointing to another
(db/belongs-to! :models/<name> :<assoc-name>
                {:model       :models/<other>
                 :foreign-key :<other>-id})

;; has-many: other model has a foreign key pointing here
(db/has-many! :models/<name> :<assoc-name>)
;; or with explicit options:
(db/has-many! :models/<name> :<assoc-name>
              {:model       :models/<other>
               :foreign-key :<name>-id})

;; has-one: like has-many but returns single record
(db/has-one! :models/<name> :<assoc-name>
             {:model       :models/<other>
              :foreign-key :<name>-id})
```

**Inference rule:** For each `:references(table)` attribute in the model's list, add a `belongs-to!` pointing to that table.

---

## Full Namespace Template

```clojure
(ns <app>.models.<singular>
  (:require [tram.db :as db]
            ;; add other requires as needed
            ))

;; --- Hooks ---

<hooks>

;; --- Associations ---

<associations>
```

---

## Real Examples from This Codebase

**User model** (`src/sample_app/models/user.clj`):
```clojure
(ns sample-app.models.user
  (:require [sample-app.concerns.authentication :refer [hash-password]]
            [tram.db :as db]))

(db/define-after-select
  :models/users
  [user]
  (dissoc user :password))

(db/define-before-insert
  :models/users
  [user]
  (update user :password hash-password))
```

**Session model** (`src/sample_app/models/session.clj`):
```clojure
(ns sample-app.models.session
  (:require [java-time.api :as jt]
            [tram.db :as db]))

(db/define-before-insert
  :models/sessions
  [session]
  (assoc session
    :expires-at (-> (jt/local-date-time)
                    (jt/plus (jt/days 2)))))
```

---

## Hydrating Associations

After defining associations, use `db/hydrate` to load them:

```clojure
;; Load a single association
(db/hydrate post :author)
;; => post with :author key populated

;; Load multiple
(db/hydrate post :author :comments)

;; Batch hydrate a list
(db/batched-hydrate posts :author)
```

---

## Instructions

1. Identify the app namespace by looking at existing model files
2. Infer hooks from the attribute list (password fields, slugs, tokens, expiry)
3. Declare `belongs-to!` for each `:references(table)` attribute
4. If you see `has-many` relationships are likely (e.g., users have posts), mention them but don't add them unless the user specified
5. Write the file to `src/<app>/models/<singular>.clj`
6. Note any helper functions the user needs to provide (like `hash-password` or `slugify`)
