(ns tram.http.router
  "Routing has a somewhat complected structure to make it easy to use, and so here
  are some terms to keep things consistent.

  - view: function that returns hiccup and takes locals.
  - handler: function that takes a request and returns a response.
  - handler-spec: map with a :handler key and possibly more metadata.
  - handler-entry: value for a method key.  Either a handler-spec, a handler, or a keyword for a view.
  - method: one of #{:get :post :put :patch :delete :options}.
  - route: map of behavior tied to a single url.  Routes are named by a :name key.

  In the following exampled:

  [\"foo\" {:name :route/foo
            :get get-foo
            :post {:handler create-foo
                   :parameters {:body FooParams}}]

  :get and :post are methods.
  get-foo and create-foo are handlers.

  {:handler create-foo
   :parameters {:body FooParams}}
  and
  get-foo

  are handler-entries.

  The second element of the vector is a route.  Note that it has a name."
  (:require [clojure.walk :refer [prewalk]]
            [methodical.core :as m]
            [potemkin :refer [import-vars]]
            [reitit.http :as http]
            [reitit.ring]
            [tram.http.lookup :refer [handlers-ns->views-ns]]
            [tram.utils :refer [evolve]]))

(defn route? [node]
  (and (map node) (:name node)))

(defn fn->var [f]
  (loop [interned-symbols (ns-map *ns*)]
    (if (empty? interned-symbols)
      nil
      (let [[_ v] (first interned-symbols)]
        (if (and (var? v)
                 (= f (var-get v)))
          v
          (recur (rest interned-symbols)))))))

(defn default-handler
  "Default handler behavior for non-fn is to set the value to be the template.

  Any valid template is acceptable."
  [template]
  (fn [_]
    {:status   200
     :template template}))

(defn handler-spec? [handler-entry]
  (and (map? handler-entry)
       (:handler handler-entry)
       (:handler-var handler-entry)))

(m/defmulti ->handler-spec
  (fn [handler-entry]
    (cond
      (keyword? handler-entry) :view-keyword
      (map? handler-entry)     :handler-spec
      (fn? handler-entry)      :handler)))

(m/defmethod ->handler-spec :default
  [handler-entry]
  (throw (ex-info "Tried to coerce invalid handler-entry."
                  {:handler-entry handler-entry})))

(m/defmethod ->handler-spec :view-keyword
  [handler-entry]
  {:handler     (default-handler handler-entry)
   :handler-var (fn->var handler-entry)})

(m/defmethod ->handler-spec :handler-spec
  [handler-entry]
  (let [handler (:handler handler-entry)]
    (when-not (:handler handler-entry)
      (throw (ex-info "Tried to coerce handler-spec without a :handler keyword."
                      {:handler-spec handler-entry})))
    (assoc handler-entry :handler-var (fn->var handler))))

(m/defmethod ->handler-spec :handler
  [handler-entry]
  (let [handler-var (fn->var handler-entry)]
    (when-not handler-var
      (throw
        (ex-info
          "Tried to coerce handler-spec fn without a corresponding handler-var."
          {:handler-spec handler-entry})))
    {:handler     handler-entry
     :handler-var handler-var}))

(defn handler-evolver
  "Evolve a handler.  Receives one of
  - handler fn
  - map with a `:handler` key
  - keyword to indicate the view fn,
    eg :view/sign-up, which points to the corresponding views
    ns and the sign-up function there.

  First convert the handler fn into a map like {:handler val}. Then find the var
  that handler fn is stored in, if any, and add that to the map under
  `:handler-var`."
  [handler-entry]
  (if (handler-spec? handler-entry)
    handler-entry
    (let [handler-spec (if (map? handler-entry)
                         handler-entry
                         {})
          handler      (if (keyword? handler-entry)
                         (default-handler handler-entry)
                         (or (:handler handler-entry)
                             handler-entry))]
      (assoc handler-spec
        :handler     handler
        :handler-var (fn->var handler)))))

(def http-verb-evolutions
  {:get    handler-evolver
   :patch  handler-evolver
   :put    handler-evolver
   :delete handler-evolver
   :post   handler-evolver})

(defmacro defroutes
  "Define routes in Tram.

  Without using `defroutes`, you won't get automatic template resolution in your
  routes. If you don't care to automatically resolve template names, then you
  can use `def` to create routes.

  The purpose of this macro is to add the namespace of your handlers to the
  route data, and to add the var reference of the handler itself to the handler
  data."
  [var-name routes]
  (let [evaluated-routes
        (prewalk (fn [n]
                   ;; This resolves any symbols (mostly for function names)
                   ;; into vars.
                   (if-let [var (and (symbol? n) (not= 'fn n) (resolve n))]
                     @var
                     n))
                 routes)]
    `(def ~var-name
       ~(prewalk (fn [node]
                   (if (route? node)
                     (evolve http-verb-evolutions
                             (update node
                                     :namespace
                                     (fn [v]
                                       (or v
                                           (str *ns*)))))
                     node))
                 evaluated-routes))))

(defn tram-router
  "`reitit.http/router` with default options for tram.

  `routes` - vector of routes.
  `options` - map of possible overrides"
  ([routes]
   (tram-router routes {}))
  ([routes options]
   (http/router routes options)))

(import-vars [reitit.ring ring-handler])
