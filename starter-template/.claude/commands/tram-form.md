# /tram-form — Generate a Form with Validation

Generate a Hiccup form, Malli schema, and handler error path — all kept in sync.

## Usage

```
/tram-form <form-name> [fields...]
```

**Examples:**
```
/tram-form create-post title:string! body:text! published:boolean
/tram-form sign-up email:email! password:password! username:string!
/tram-form update-profile display-name:string bio:text avatar-url:string
/tram-form contact-form name:string! email:email! message:text! subject:select(general,bug,feature)
```

## Field Syntax

```
<name>[:<type>][!]

Types:
  string      Text input
  text        Textarea
  email       Email input (type="email")
  password    Password input (type="password")
  integer     Number input (type="number")
  boolean     Checkbox
  date        Date input (type="date")
  select(a,b) Select/dropdown with options a, b

Modifiers:
  !           Required field (client-side required + server-side Malli validation)
```

## What to Generate

Three coordinated pieces:

1. **Hiccup form** — with HTMX wiring, input names, and error display
2. **Malli schema** — for the route's `:parameters {:body ...}`
3. **Handler error path** — re-rendering the form on validation failure

---

## 1. Hiccup Form

The form uses HTMX to post and display validation errors inline without a page reload.

```clojure
(defn <form-name>-form
  ([]
   (<form-name>-form {} {}))
  ([values errors]
   [:form {:hx-post   :route/<resource>/<action>
           :hx-target "#<form-name>-errors"
           :class     "space-y-4"}
    [:div {:id "<form-name>-errors"}
     (when (seq errors)
       [:div {:class "text-red-500 text-sm"}
        (for [[field msg] errors]
          [:p {:key (name field)} msg])])]

    ;; Text / string field
    [:div {:class "flex flex-col space-y-1"}
     [:label {:for "<field-name>"} "<Label>"]
     [:input {:name     "<field-name>"
              :id       "<field-name>"
              :type     :text
              :value    (:<field-name> values)
              :required <true-if-required>
              :class    "border rounded px-2 py-1"}]]

    ;; Textarea
    [:div {:class "flex flex-col space-y-1"}
     [:label {:for "<field-name>"} "<Label>"]
     [:textarea {:name     "<field-name>"
                 :id       "<field-name>"
                 :required <true-if-required>
                 :class    "border rounded px-2 py-1"}
      (:<field-name> values)]]

    ;; Checkbox (boolean)
    [:div {:class "flex items-center space-x-2"}
     [:input {:name    "<field-name>"
              :id      "<field-name>"
              :type    :checkbox
              :checked (:<field-name> values)
              :class   "rounded"}]
     [:label {:for "<field-name>"} "<Label>"]]

    ;; Select
    [:div {:class "flex flex-col space-y-1"}
     [:label {:for "<field-name>"} "<Label>"]
     [:select {:name  "<field-name>"
               :id    "<field-name>"
               :class "border rounded px-2 py-1"}
      (for [opt ["<option1>" "<option2>"]]
        [:option {:value opt :selected (= opt (:<field-name> values))} opt])]]

    [:button {:type  :submit
              :class "rounded py-2 px-4 bg-blue-600 text-white hover:bg-blue-700"}
     "<Submit Label>"]]))
```

### Form Arity Pattern

Always generate the form function with two arities:
- `(form-fn)` — zero args, empty form for new/create
- `(form-fn values errors)` — with pre-filled values (for edit) and error messages

This lets you reuse the same form for both create and edit, and for re-rendering with errors.

---

## 2. Malli Schema

Place in the route definition under `:parameters {:body ...}`.

```clojure
;; All required fields
[:map
 [:title :string]
 [:email [:re #"[^@]+@[^@]+\.[^@]+"]]
 [:password [:string {:min 8}]]
 [:published :boolean]]

;; Mixed required and optional
[:map
 [:title :string]
 [:body {:optional true} :string]
 [:published {:optional true} :boolean]]
```

**Type mapping:**

| Field type | Malli type |
|------------|------------|
| `string`   | `:string` |
| `text`     | `:string` |
| `email`    | `[:re #"[^@]+@[^@]+"]` or `:string` |
| `password` | `[:string {:min 8}]` |
| `integer`  | `:int` |
| `boolean`  | `:boolean` |
| `date`     | `:string` (ISO date as string from form) |
| `select`   | `[:enum "opt1" "opt2"]` |

Required fields (marked with `!`) are top-level map keys. Optional fields use `{:optional true}`:

```clojure
;; Required
[:map [:name :string]]

;; Optional
[:map [:name {:optional true} :string]]
```

---

## 3. Handler Error Path

The route/handler pattern for forms with validation:

```clojure
;; Route definition with Malli schema
["/<resource>"
 {:name :route/<resource>/create
  :post {:handler    create-handler
         :parameters {:body [:map
                              [:title :string]
                              [:body {:optional true} :string]]}}}]

;; Handler — success path redirects, error path re-renders form
(defn create-handler [req]
  (let [params (get-in req [:parameters :body])]
    (try
      (db/insert-returning-instance! :models/<resource> params)
      (redirect :route/<resource>/index)
      (catch Exception e
        {:status 422
         :body   (views/<form-name>-form params {"base" (ex-message e)})}))))

;; For custom validation errors (when you need logic beyond Malli):
(defn create-handler [req]
  (let [params (get-in req [:parameters :body])
        errors (validate-params params)]
    (if (seq errors)
      {:status 422
       :body   (views/<form-name>-form params errors)}
      (do
        (db/insert-returning-instance! :models/<resource> params)
        (redirect :route/<resource>/index)))))

(defn validate-params [params]
  (cond-> {}
    (< (count (:password params)) 8)
    (assoc :password "Password must be at least 8 characters")

    (not= (:password params) (:password-confirm params))
    (assoc :password-confirm "Passwords do not match")))
```

### How Malli Coercion Errors Work

When a request body fails Malli validation, Tram's `exception-interceptor` calls the route's `:error` handler (if defined) or the default error handler. To customize:

```clojure
["/<resource>"
 {:name :route/<resource>/create
  :post {:handler    create-handler
         :error      (fn [schema req]
                       {:status 422
                        :body   (views/<form-name>-form (:body req) {:base "Invalid input"})})
         :parameters {:body [...]}}}]
```

---

## HTMX Config for 422 Swap

Tram's `as-full-page` includes this HTMX config that enables swapping on 422 responses:

```json
{"code":"422", "swap": true}
```

This means returning `{:status 422 :body html}` from a handler will swap the content into the target — no special setup needed.

---

## Complete Example

For `/tram-form sign-up email:email! password:password! username:string!`:

**View** (`src/<app>/views/authentication_views.clj`):
```clojure
(defn sign-up-form
  ([]
   (sign-up-form {} {}))
  ([values errors]
   [:div {:class "max-w-md mx-auto mt-10"}
    [:div {:class "p-6 border rounded shadow bg-blue-50 space-y-6"}
     [:h1 {:class "text-2xl"} "Create an Account"]
     [:form {:hx-post   :route/sign-up
             :hx-target "#sign-up-errors"
             :class     "space-y-4"}
      [:div#sign-up-errors
       (when (seq errors)
         [:div {:class "text-red-500 text-sm"}
          (for [[_ msg] errors]
            [:p msg])])]
      [:div {:class "flex flex-col space-y-1"}
       [:label {:for "email"} "Email"]
       [:input {:name     "email"
                :id       "email"
                :type     :email
                :value    (:email values)
                :required true
                :class    "border bg-white rounded px-2 py-1"}]]
      [:div {:class "flex flex-col space-y-1"}
       [:label {:for "username"} "Username"]
       [:input {:name     "username"
                :id       "username"
                :type     :text
                :value    (:username values)
                :required true
                :class    "border bg-white rounded px-2 py-1"}]]
      [:div {:class "flex flex-col space-y-1"}
       [:label {:for "password"} "Password"]
       [:input {:name     "password"
                :id       "password"
                :type     :password
                :required true
                :class    "border bg-white rounded px-2 py-1"}]]
      [:button {:type  :submit
                :class "w-full rounded py-2 px-4 bg-blue-600 text-white hover:bg-blue-700"}
       "Create Account"]]]]))
```

**Route** (`src/<app>/handlers/authentication_handlers.clj`):
```clojure
(defroutes routes
  [["/sign-up"
    {:name :route/sign-up
     :get  :view/sign-up
     :post {:handler    sign-up-post-handler
            :parameters {:body [:map
                                 [:email :string]
                                 [:username :string]
                                 [:password [:string {:min 8}]]]}}}]])
```

**Handler**:
```clojure
(defn sign-up-post-handler [req]
  (let [params (get-in req [:parameters :body])]
    (if-let [user (register-new-account params)]
      (redirect :route/dashboard)
      {:status 422
       :body   (views/sign-up-form params {:base "An account with that email already exists"})})))
```

---

## Instructions

1. Parse the field list and infer types, labels, and required status
2. Generate all three pieces — form view function, Malli schema, handler error path
3. Use the app's existing namespace conventions (check existing handler/view files)
4. For the form action route, use the route name from context or ask the user to supply it
5. Apply Tailwind classes consistent with existing forms in the project (check authentication views for the style)
