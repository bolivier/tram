# /tram-rhizome — Generate a Rhizome Interaction

Generate the trigger element, handler, partial view, and route entry for a
rhizome interaction.

## Usage

```
/tram-rhizome <description>
```

**Examples:**
```
/tram-rhizome inline-edit post title
/tram-rhizome live search users by email
/tram-rhizome toggle post published status
/tram-rhizome load comments for a post on demand
/tram-rhizome delete a comment
```

Describe the interaction in plain English. The more specific, the better.

## What to Generate

Four coordinated pieces:

1. **Trigger element** — Hiccup with rhizome attributes
2. **Handler** — checks `rhizome-request?`, returns partial
3. **Partial view** — the fragment to morph in
4. **Route entry** — add to `routes.clj`

---

## How Rhizome Works in Tram

Rhizome is Tram's client runtime, loaded as `/assets/js/rhizome.js` in
`as-full-page`. Every request it makes carries the header
`rhizome-request: true`. Tram reads that header to return a partial rather
than a full page.

**CSRF is automatic.** The layout's `(tr/csrf-meta-tag)` holds the token,
and rhizome sends it as `x-csrf-token` on every non-GET request. Rhizome
forms need no `csrf-hidden-field`; only plain HTML forms do.

Check whether a request came from rhizome in a handler:

```clojure
(require '[tram.impl.http :refer [rhizome-request?]])

(defn my-handler [req]
  (if (rhizome-request? req)
    {:status 200 :body (views/my-partial ctx)}
    {:status 200}))  ;; fall back to full page
```

**Morph targets by id.** Every top-level element in a partial response needs
an `id`, and that id must exist on the page. Rhizome replaces the page
element whole (outerHTML). A response with several top-level elements
updates several places at once. An element with no id, or an id not on the
page, is dropped with a console warning.

---

## Rhizome Attributes in Hiccup

Require the keyword namespace in every view that uses rhizome:

```clojure
(ns my-app.views.post-views
  (:require [rhizome.core :as rz]))
```

### Triggers

A trigger names an event and holds a directive map. `:do` names the command.

```clojure
;; POST on click
[:button {::rz/click {:do       :http/post
                      :http/url :route/posts.toggle}}
 "Publish"]

;; POST on form submit
[:form {::rz/submit {:do       :http/post
                     :http/url :route/posts.create}}
 ...]

;; POST on input (live search)
[:input {::rz/input {:do       :http/post
                     :http/url :route/users.search}}]

;; GET when the element mounts (lazy load, open a stream)
[:div {::rz/load {:do       :http/get
                  :http/url :route/posts.comments}}]
```

Commands: `:http/get`, `:http/post`, `:http/patch`, `:dom/remove`,
`:dom/navigate`.

**Form data rides non-GET requests.** A `:http/post` or `:http/patch` sends
the closest enclosing form as edn. GET sends no body, so use POST whenever
the server needs input values. An element outside a form sends no body.

**File inputs work with no extra wiring.** A form carrying a selected file
goes as multipart instead of edn, and the server puts it back together. The
handler reads files from `[:parameters :body]` next to every other field. A
file arrives as `tram.routes/File`:

```clojure
{:filename "visa.png" :content-type "image/png" :size 3401 :tempfile #object[java.io.File]}
```

`:tempfile` is deleted once the response finishes. Copy or stream it inside
the handler if you want to keep the bytes.

Route keywords like `:route/name` expand to URL paths anywhere in hiccup,
including inside directive maps. Use `tram.routes/make-route` for routes
with path params:

```clojure
[:button {::rz/click {:do       :http/post
                      :http/url (tram.routes/make-route :route/posts.toggle
                                                        {:id (:id post)})}}
 "Publish"]
```

### Bindings (client-only state)

Signals hold client state. Bindings read and write them without a server
round trip.

```clojure
[:span {::rz/text :query}]              ;; text content follows the signal
[:span {::rz/show :menu-open?} "..."]   ;; hidden while the signal is falsey
[:input {::rz/bind [:query ""]}]        ;; two-way: control <-> signal
[:pre {::rz/debug true}]                ;; dev aid: prints the signal store
```

### Streams

Any endpoint can stream. The response decides, not the element. A handler
returns `:stream` with server-sent events, and each event names a command
that rhizome runs (a morph, for instance). Open a stream with `::rz/load`:

```clojure
[:div {::rz/load {:do       :http/get
                  :http/url :route/posts.updates}}
 ...]
```

The stream closes when the element unmounts.

### Navigation

A handler that redirects works the same for rhizome and plain requests.
Return `(redirect :route/name)`. Fetch follows the 303, rhizome sees
the followed redirect, and loads the final URL as a full page.

A stream can also navigate by naming the command:

```
event: dom/navigate
data: {:dom/url "/dashboard"}
```

---

## Common Interaction Patterns

### Inline Edit

```clojure
;; In view: show value, click to edit
(defn title-display [post]
  [:span {:id        (str "title-" (:id post))
          ::rz/click {:do       :http/get
                      :http/url (tram.routes/make-route :route/posts.edit-title
                                                        {:id (:id post)})}}
   (:title post)])

;; Partial: the edit form, same id so it morphs into place
(defn title-edit-form [post]
  [:form {:id         (str "title-" (:id post))
          ::rz/submit {:do       :http/post
                       :http/url (tram.routes/make-route
                                   :route/posts.update-title
                                   {:id (:id post)})}}
   [:input {:name  "title"
            :value (:title post)
            :type  "text"}]
   [:button {:type :submit} "Save"]])

;; Handlers: each returns the partial that replaces the id
(defn edit-title [req]
  (let [id   (get-in req [:path-params :id])
        post (db/select-one :models/posts id)]
    {:status 200 :body (views/title-edit-form post)}))

(defn update-title [req]
  (let [id    (get-in req [:path-params :id])
        title (get-in req [:parameters :body :title])]
    (db/update! :models/posts id {:title title})
    {:status 200 :body (views/title-display (db/select-one :models/posts id))}))
```

### Live Search

POST, not GET, so the input value rides the request body.

```clojure
[:form
 [:input {:name      "q"
          :type      "search"
          ::rz/input {:do       :http/post
                      :http/url :route/users.search}}]]

[:ul#search-results]

;; Partial keeps the id
(defn search-results [users]
  [:ul#search-results
   (for [user users]
     [:li {:key (:id user)} (:email user)])])

;; Handler
(defn search [req]
  (let [q     (get-in req [:parameters :body :q] "")
        users (db/select :models/users :email [:like (str "%" q "%")])]
    {:status 200 :body (views/search-results users)}))
```

### Toggle

```clojure
(defn publish-toggle [post]
  [:button {:id        (str "publish-toggle-" (:id post))
            ::rz/click {:do       :http/post
                        :http/url (tram.routes/make-route
                                    :route/posts.toggle-published
                                    {:id (:id post)})}}
   (if (:published post) "Unpublish" "Publish")])

(defn toggle-published [req]
  (let [id   (get-in req [:path-params :id])
        post (db/select-one :models/posts id)
        post (db/update! :models/posts id {:published (not (:published post))})]
    {:status 200 :body (views/publish-toggle post)}))
```

### Delete

Return the updated container under its id. The removed row disappears with
the morph.

```clojure
[:button {::rz/click {:do       :http/post
                      :http/url (tram.routes/make-route :route/posts.delete
                                                        {:id (:id post)})}}
 "Delete"]

(defn delete [req]
  (let [id (get-in req [:path-params :id])]
    (db/delete! :models/posts id)
    {:status 200 :body (views/post-list (db/select :models/posts))}))
```

---

## What Rhizome Does Not Do Yet

- **Partial-page navigation.** `:dom/navigate` is a full page load. No
  `pushState`, no history integration.
- **Confirmation dialogs.** No `hx-confirm` equivalent.

---

## Route Entry Format

```clojure
;; Simple handler
["/posts/:id/toggle-published"
 {:name :route/posts.toggle-published
  :post toggle-published-handler}]

;; With Malli parameters
["/users/search"
 {:name :route/users.search
  :post {:handler    search-handler
         :parameters {:body [:map [:q {:optional true} :string]]}}}]
```

---

## Instructions

Read the interaction description and:

1. Identify the **trigger** (what user action starts it), the **target id**
   (what element the response replaces), and the **HTTP method** (GET for
   reads without input, POST for mutations and anything sending form data)
2. Generate the trigger element with the correct `::rz/*` attribute
3. Give the partial's top-level element the same id as the page element it
   replaces
4. Generate the handler that checks `rhizome-request?` where appropriate
5. Generate the route entry
6. Note any DB queries or model operations needed
7. Identify the correct namespace — look at existing handler/view files to
   find the app namespace
