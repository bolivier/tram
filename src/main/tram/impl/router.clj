(ns tram.impl.router
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
  (:require [clojure.set :as set]
            [clojure.zip :as zip]
            [methodical.core :as m]
            [reitit.interceptor :as interceptor]
            [reitit.ring]
            [tram.language :as lang]))

(defn- invoke-through-var
  "Wrap `handler-var` so each request invokes through the var rather than the fn
  it holds right now.

  This is what lets a redefined handler take effect without rebuilding the
  router."
  [handler-var]
  (fn [request] (handler-var request)))

(extend-protocol interceptor/IntoInterceptor
  clojure.lang.Var
  (into-interceptor [this data opts]
    (interceptor/into-interceptor (if (fn? @this)
                                    (invoke-through-var this)
                                    @this)
                                  data
                                  opts)))

(def HandlerSpecSchema
  [:map [:handler fn?]])

(def Interceptor
  [:map
   [:name :keyword]
   [:enter {:optional true}
    :fn]
   [:leave {:optional true}
    :fn]])

(def RouteSchema
  [:map
   [:name [:qualified-keyword {:namespace :route}]]
   [:layout [:or fn? [:fn :var?] lang/ViewKeyword]]
   [:interceptors [:vector Interceptor]]
   [:get {:optional true}
    HandlerSpecSchema]
   [:post {:optional true}
    HandlerSpecSchema]
   [:put {:optional true}
    HandlerSpecSchema]
   [:patch {:optional true}
    HandlerSpecSchema]
   [:delete {:optional true}
    HandlerSpecSchema]])

(def HandlEntrySchema
  [:enum [fn? var? [:qualified-keyword {:namespace :view}]]])

(def verbs
  #{:get :put :patch :post :delete})

(defn has-verb? [node]
  (set/intersection verbs (into #{} (keys node))))

(defn route? [node]
  ;; TODO this feels like a hack. Need a real concept for what this is.
  (and (map? node) (or (:name node) (:layout node))))

(defn default-handler
  "Default handler for endpoints.  Simply return a 200 status code.

  Any valid template is acceptable."
  [_]
  {:status 200})

(defn get-automagic-template-symbol
  "The view symbol for `sym`, quoted for emission into route data.

  Emitting the symbol rather than the resolved view defers resolution to request
  time, so a view written after its route was compiled still renders, and an
  edited view takes effect without re-evaluating the routes."
  [sym]
  (list 'quote (lang/view-symbol *ns* sym)))

(m/defmulti ->handler-spec
  (fn [handler-entry]
    (cond
      (keyword? handler-entry) :view-keyword
      (symbol? handler-entry)  :symbol-spec
      (map? handler-entry)     :handler-spec
      (fn? handler-entry)      :handler
      (var? handler-entry)     :var
      (list? handler-entry)    :list)))

(m/defmethod ->handler-spec :default
  [handler-entry]
  (throw (ex-info (str "Unexpected handler-entry: " handler-entry)
                  {:bad-handler-entry handler-entry})))

(m/defmethod ->handler-spec :symbol-spec
  [handler-entry]
  {:handler  (list 'var (symbol (str *ns*) (str (name handler-entry))))
   :template (get-automagic-template-symbol handler-entry)})

(m/defmethod ->handler-spec :view-keyword
  [handler-entry]
  ;; A route named by a view keyword renders through `default-handler`, so
  ;; unlike a route with a handler of its own, its view is not optional.
  {:handler        `default-handler
   :template       (get-automagic-template-symbol handler-entry)
   :view-required? true})

(m/defmethod ->handler-spec :handler-spec
  [handler-entry]
  (when-not (:handler handler-entry)
    (throw (ex-info "Tried to coerce handler-spec without a :handler keyword."
                    {:handler-spec handler-entry})))
  (-> handler-entry
      (cond->
        (not (contains? handler-entry :template))
        (assoc :template
          (get-automagic-template-symbol (name (:handler handler-entry)))))
      (update :handler
              (fn [handler]
                (if (symbol? handler)
                  (list 'var
                        handler)
                  handler)))))

(m/defmethod ->handler-spec :list
  [handler-entry]
  {:handler handler-entry})

(def verb?
  "Convenience fn to make validating easier."
  verbs)

(defn layout-interceptor
  "Mostly private interceptor for adding layouts from a layout key in the route to
  a `:layouts` key in the context.

  This is dynamically injected into `:interceptors` for a route that has a
  `:layout` key."
  [layout-fn]
  {:name  ::layout-interceptor
   :enter (fn [ctx]
            (update ctx
                    :layouts
                    (fn [layouts] (conj (or layouts []) layout-fn))))})

(defn coerce-route-entries-to-specs [route]
  (reduce (fn [route k]
            (cond
              (= :layout k)
              (update route
                      :interceptors
                      (fn [interceptors]
                        (conj (or interceptors [])
                              (let [layout-value (get route k)]
                                (if (keyword? layout-value)
                                  (list `layout-interceptor
                                        (lang/view-symbol *ns*
                                                          layout-value))
                                  `(layout-interceptor ~layout-value))))))

              (verb? k) (update route k ->handler-spec)
              :else route))
    ;; This makes sure that namespace can be overridden by a user.
    (update route :namespace (fn [v] (or v (str *ns*))))
    (filter route (conj verbs :layout))))

(defn map-routes
  "Walk a routing tree and apply `f` to the route maps."
  [f routes]
  (loop [zipper (zip/vector-zip routes)]
    (if (zip/end? zipper)
      (-> zipper
          zip/root)
      (recur (zip/next (if (route? (zip/node zipper))
                         (zip/edit zipper
                                   f)
                         zipper))))))
