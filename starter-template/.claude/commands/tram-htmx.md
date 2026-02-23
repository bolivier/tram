# /tram-htmx — Generate an HTMX Interaction

Generate the trigger element, handler, partial view, and route entry for an HTMX interaction.

## Usage

```
/tram-htmx <description>
```

**Examples:**
```
/tram-htmx inline-edit post title
/tram-htmx infinite scroll posts list
/tram-htmx live search users by email
/tram-htmx toggle post published status
/tram-htmx load comments for a post on demand
/tram-htmx delete a comment with confirmation
```

Describe the interaction in plain English. The more specific, the better.

## What to Generate

Four coordinated pieces:

1. **Trigger element** — Hiccup with HTMX attributes
2. **Handler** — checks `htmx-request?`, returns partial
3. **Partial view** — the fragment to swap in
4. **Route entry** — add to `routes.clj`

---

## How HTMX Works in Tram

Tram's `wrap-page-interceptor` detects HTMX requests by checking the `hx-request` header. When present, the response body is returned as-is (no full-page wrapper). This means:

- Full-page requests get `<html><head>...</head><body>...</body></html>`
- HTMX requests get just the returned hiccup fragment

Check whether a request is HTMX in a handler:

```clojure
(require '[tram.impl.http :refer [htmx-request?]])

(defn my-handler [req]
  (if (htmx-request? req)
    {:status 200 :body (views/my-partial ctx)}
    {:status 200}))  ;; fall back to full page
```

**Important:** Return `{:status 422 :body (views/partial)}` to swap even on "error" responses. The HTMX config in `as-full-page` enables 422 swaps:
```json
{"code":"422", "swap": true}
```

---

## HTMX Attributes in Hiccup

```clojure
;; GET request on click
[:button {:hx-get    "/path"
          :hx-target "#result-div"
          :hx-swap   "innerHTML"}
 "Load"]

;; POST request
[:form {:hx-post   :route/create
        :hx-target "#errors"
        :hx-swap   "innerHTML"}
 ...]

;; Trigger options
:hx-trigger "click"           ;; default for buttons
:hx-trigger "change"          ;; for inputs/selects
:hx-trigger "input delay:300ms"  ;; debounced input
:hx-trigger "intersect"       ;; infinite scroll
:hx-trigger "revealed"        ;; when element becomes visible

;; Swap targets
:hx-target "#some-id"         ;; CSS selector
:hx-target "closest .parent"  ;; relative selector
:hx-target "this"             ;; the element itself

;; Swap strategies
:hx-swap "innerHTML"          ;; replace inner HTML (default)
:hx-swap "outerHTML"          ;; replace the element itself
:hx-swap "beforeend"          ;; append to end
:hx-swap "afterend"           ;; insert after element
:hx-swap "delete"             ;; delete the target element

;; Additional attributes
:hx-push-url "true"           ;; update browser URL
:hx-confirm "Are you sure?"   ;; confirmation dialog
:hx-vals "{\"key\": \"val\"}" ;; extra JSON values to send
:hx-include "#other-form"     ;; include values from another form
:hx-boost "true"              ;; progressive enhancement for links
```

Route keywords like `:route/name` auto-expand to URL paths in Hiccup.

---

## Common Interaction Patterns

### Inline Edit

```clojure
;; In view: show value, click to edit
(defn title-display [post]
  [:span {:id         (str "title-" (:id post))
          :hx-get     (tram.routes/make-route :route/posts/edit-title {:id (:id post)})
          :hx-target  (str "#title-" (:id post))
          :hx-swap    "outerHTML"
          :hx-trigger "click"}
   (:title post)])

;; Partial: the edit form
(defn title-edit-form [post]
  [:form {:hx-post   (tram.routes/make-route :route/posts/update-title {:id (:id post)})
          :hx-target (str "#title-" (:id post))
          :hx-swap   "outerHTML"}
   [:input {:name  "title"
            :value (:title post)
            :type  "text"}]
   [:button {:type :submit} "Save"]
   [:button {:type    "button"
             :hx-get  (tram.routes/make-route :route/posts/show {:id (:id post)})
             :hx-target (str "#title-" (:id post))
             :hx-swap "outerHTML"}
    "Cancel"]])

;; Handler
(defn edit-title [req]
  (if (htmx-request? req)
    (let [id (get-in req [:path-params :id])
          post (db/select-one :models/posts id)]
      {:status 200 :body (views/title-edit-form post)})
    (redirect :route/posts/show {:id (get-in req [:path-params :id])})))

(defn update-title [req]
  (let [id    (get-in req [:path-params :id])
        title (get-in req [:parameters :body :title])]
    (db/update! :models/posts id {:title title})
    (let [post (db/select-one :models/posts id)]
      {:status 200 :body (views/title-display post)})))
```

### Live Search

```clojure
;; Trigger input (debounced)
[:input {:name        "q"
         :type        "search"
         :hx-get      :route/users/search
         :hx-target   "#search-results"
         :hx-trigger  "input delay:300ms"
         :placeholder "Search users..."}]

[:div#search-results]

;; Partial: search results
(defn search-results [users]
  [:ul
   (for [user users]
     [:li {:key (:id user)} (:email user)])])

;; Handler
(defn search [req]
  (let [q     (get-in req [:parameters :query :q] "")
        users (db/select :models/users :email [:like (str "%" q "%")])]
    {:status 200 :body (views/search-results users)}))
```

### Infinite Scroll

```clojure
;; Last item in list triggers load of next page
(defn post-list-item [post last?]
  (let [base [:li (:title post)]]
    (if last?
      (conj base {:hx-get     (tram.routes/make-route :route/posts/page
                                                       {:page (inc current-page)})
                  :hx-trigger "revealed"
                  :hx-target  "closest ul"
                  :hx-swap    "beforeend"})
      base)))
```

### Toggle / Optimistic Update

```clojure
;; Toggle button
(defn publish-toggle [post]
  [:button {:hx-post   (tram.routes/make-route :route/posts/toggle-published {:id (:id post)})
            :hx-target "this"
            :hx-swap   "outerHTML"}
   (if (:published post) "Unpublish" "Publish")])

;; Handler returns updated button
(defn toggle-published [req]
  (let [id   (get-in req [:path-params :id])
        post (db/select-one :models/posts id)
        post (db/update! :models/posts id {:published (not (:published post))})]
    {:status 200 :body (views/publish-toggle post)}))
```

### Delete with Confirmation

```clojure
;; Delete button using hx-confirm
[:button {:hx-delete  (tram.routes/make-route :route/posts/delete {:id (:id post)})
          :hx-target  "closest li"
          :hx-swap    "outerHTML"
          :hx-confirm "Delete this post? This cannot be undone."}
 "Delete"]

;; Handler returns empty string (removes element)
(defn delete [req]
  (let [id (get-in req [:path-params :id])]
    (db/delete! :models/posts id)
    {:status 200 :body ""}))
```

---

## Route Entry Format

```clojure
;; Simple handler
["/posts/:id/toggle-published"
 {:name :route/posts/toggle-published
  :post toggle-published-handler}]

;; With Malli parameters
["/users/search"
 {:name :route/users/search
  :get  {:handler    search-handler
         :parameters {:query [:map [:q {:optional true} :string]]}}}]
```

---

## Instructions

Read the interaction description and:

1. Identify the **trigger** (what user action initiates it), the **target** (what DOM element gets updated), and the **HTTP method** (GET for reads, POST/DELETE for mutations)
2. Generate the trigger element with correct `hx-*` attributes
3. Generate the handler that checks `htmx-request?` where appropriate
4. Generate the partial view function
5. Generate the route entry
6. Note any DB queries or model operations needed
7. Identify the correct namespace — look at existing handler/view files to find the app namespace
