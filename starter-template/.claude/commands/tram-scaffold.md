---
name: tram-scaffold
description: Generate handler, view, and route entries for a new resource with full CRUD. 
---

## How to Run this Skill

Start a conversation with the user.  Ask questions until you have enough to
write the Clojure code, then write those files.

The <resource> value is likely a db table. Use Toucan2 models (`:models/<resource>`) for all db operations.

At the end of running this skill, a user should have a new handlers ns that
exports a `defroutes` var called routes, a new views ns with view functions
matching each handler name, and a route entry added to `routes.clj`.

## What to Generate

Three coordinated files that wire together automatically via Tram's naming conventions:

1. **Handler namespace** — `src/<app>/handlers/<singular>_handlers.clj`
2. **View namespace** — `src/<app>/views/<singular>_views.clj`
3. **Route entry** — snippet to add to `src/<app>/routes.clj`

---

## 1. Handler Namespace

File: `src/<app>/handlers/<singular>_handlers.clj`

```clojure
(ns <app>.handlers.<singular>-handlers
  (:require [<app>.views.<singular>-views :as views]
            [tram.core :refer [defroutes]]
            [tram.db :as db]
            [tram.routes :refer [redirect]]))

(defn index-page [req]
  {:status 200
   :locals {:<resource> (db/select :models/<resource>)}})

(defn show-page [req]
  (let [id (get-in req [:parameters :path :id])]
    {:status 200
     :locals {:<singular> (db/select-one :models/<resource> id)}}))

(defn new-form-page [req]
  {:status 200})

(defn create [req]
  (let [params (get-in req [:parameters :body])]
    (db/insert-returning-instance! :models/<resource> params)
    (redirect :route/<resource>-index)))

(defn edit-page [req]
  (let [id (get-in req [:parameters :path :id])]
    {:status 200
     :locals {:<singular> (db/select-one :models/<resource> id)}}))

(defn update [req]
  (let [id     (get-in req [:parameters :path :id])
        params (get-in req [:parameters :body])]
    (db/update! :models/<resource> id params)
    (redirect :route/<resource>-show {:id id})))

(defn delete [req]
  (let [id (get-in req [:parameters :path :id])]
    (db/delete! :models/<resource> id)
    (redirect :route/<resource>-index)))

(defroutes routes
  [["/<resource>"
    [""
     {:name :route/<resource>-index
      :get  index-page
      :post {:handler    create
             :parameters {:body <malli-schema>}}}]
    ["/new"
     {:name :route/<resource>-new
      :get  new-form-page}]]
   
   ["/<singular>/:id"
    {:parameters [:path [:id :int]]}
    [""
     {:name :route/<resource>-show
      :get  show-page}]
    ["/edit"
     {:name :route/<resource>-edit
      :get  edit-page
      :post {:handler    update
             :parameters {:body <malli-schema>}}}]
    ["/delete"
     {:name :route/<resource>-delete
      :post delete}]]])
```

**Key handler patterns:**
- `{:status 200}` — Tram auto-resolves the view by matching namespace/function names
- `{:status 200 :locals {:key val}}` — passes data to the view as the `ctx` argument
- `(redirect :route/name)` — HTMX-compatible redirect (uses `hx-redirect` header)
- `(redirect :route/name {:id id})` — redirect with path params

---

## 2. View Namespace

File: `src/<app>/views/<singular>_views.clj`

```clojure
(ns <app>.views.<singular>-views
  (:require [tram.vars :refer [*current-user*]]))

(defn index-page [ctx]
  (let [<resource> (:<resource> ctx)]
    [:div
     [:h1 "<Resource>"]
     [:a {:href :route/<resource>-new} "New <singular>"]
     [:ul
      (for [item <resource>]
        [:li {:key (:id item)}
         [:a {:href (tram.routes/make-route :route/<resource>-show {:id (:id item)})}
          (<display-field> item)]
         " "
         [:a {:href (tram.routes/make-route :route/<resource>-edit {:id (:id item)})}
          "Edit"]])]]))

(defn show-page [ctx]
  (let [<singular> (:<singular> ctx)]
    [:div
     [:h1 (<display-field> <singular>)]
     ;; display fields here
     [:a {:href :route/<resource>-index} "Back"]
     [:a {:href (tram.routes/make-route :route/<resource>-edit {:id (:id <singular>)})}
      "Edit"]]))

(defn new-form-page [_ctx]
  [:div
   [:h1 "New <singular>"]
   [:form {:hx-post   :route/<resource>-index
           :hx-target "#errors"}
    [:div#errors]
    ;; form fields here
    <form-fields>
    [:button {:type :submit} "Create <singular>"]]])

(defn edit-page [ctx]
  (let [<singular> (:<singular> ctx)]
    [:div
     [:h1 "Edit <singular>"]
     [:form {:hx-post   (tram.routes/make-route :route/<resource>-edit {:id (:id <singular>)})
             :hx-target "#errors"}
      [:div#errors]
      ;; form fields with current values
      <form-fields-with-values>
      [:button {:type :submit} "Save"]]]))
```

**View patterns:**
- Views receive the `ctx` map (the `:locals` from the handler response)
- Use `*current-user*` from `tram.vars` for auth info
- Route keywords in hiccup (`:route/name`) auto-expand to URL paths
- Use `tram.routes/make-route` for routes with path params

---

## 3. Route Entry

Snippet to require and include in `src/<app>/routes.clj`:

```clojure
;; Add to ns :require
[<app>.handlers.<singular>-handlers :as <singular>.handlers]

;; Add to route tree (alongside other handler routes)
<singular>.handlers/routes
```

---

## Handler-View Auto-Wiring

Tram automatically resolves views from handler responses when:
- Handler returns `{:status 200}` (no explicit `:template`)
- A function exists in `<app>.views.<singular>-views` with the same name as the handler function

So `index-page` handler → auto-resolves to `<app>.views.<singular>-views/index-page`.

To override: `{:status 200 :template #'some.ns/other-fn}`

---

## Generate Accordingly

Look at the attribute list and:
1. Substitute the real app namespace (look at existing handler files to find it)
2. Generate actual form fields for `new-form` and `edit` views
3. Generate the real Malli schema based on the attributes
4. Use the first non-id, non-reference, non-timestamp attribute as the display field in `index` and `show`
5. <singular> should be the singular version, and <resource> the plural.  This
   value needs to be correct per the declensia Clojure lib. 
