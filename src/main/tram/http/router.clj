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
  (:require [clojure.set :as set]
            [clojure.walk :refer [prewalk]]
            [clojure.zip :as zip]
            [methodical.core :as m]
            [potemkin :refer [import-vars]]
            [reitit.http :as http]
            [reitit.ring]
            [tram.http.interceptors :refer [layout-interceptor]]
            [tram.http.lookup :refer [handlers-ns->views-ns]]))

(def HandlerSpecSchema
  [:map [:handler fn?] [:handler-var [:fn var?]]])

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
   [:layout [:or fn? [:fn :var?] [:qualified-keyword {:namespace :view}]]]
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
  (cond
    (and (map? node) (not (:name node)) (not (:layout node)) (has-verb? node))
    (throw (ex-info "broke"
                    {:issue "Route is missing name key"
                     :node  node}))

    :else (and (map? node) (or (:name node) (:layout node)))))

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

(m/defmulti ->handler-spec
  (fn [handler-entry]
    (cond
      (keyword? handler-entry) :view-keyword
      (map? handler-entry)     :handler-spec
      (fn? handler-entry)      :handler
      (var? handler-entry)     :var
      (list? handler-entry)    :list)))

(m/defmethod ->handler-spec :default
  [handler-entry]
  (throw (ex-info "Unexpected handler entry." {:handler-entry handler-entry})))

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

(m/defmethod ->handler-spec :list
  [handler-entry]
  {:handler handler-entry})

(m/defmethod ->handler-spec :var
  [handler-entry]
  {:handler     @handler-entry
   :handler-var handler-entry})

(m/defmethod ->handler-spec :handler
  [handler-entry]
  (let [handler-var (fn->var handler-entry)]
    {:handler     handler-entry
     :handler-var handler-var}))



(def verb?
  "Convenience fn to make validating easier."
  verbs)

(defn coerce-route-entries-to-specs [route]
  (reduce (fn [route k]
            (cond
              (= :layout k)
              (update route
                      :interceptors
                      (fn [interceptors]
                        (conj (or interceptors [])
                              (let [v (get route k)]
                                (cond
                                  (keyword? v)
                                  (layout-interceptor
                                    (requiring-resolve
                                      (symbol (str (handlers-ns->views-ns *ns*))
                                              (name v))))

                                  (symbol? v) (layout-interceptor (resolve v))
                                  :else (layout-interceptor v))))))

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

(defn replace-symbols-with-vars
  "Walks the tree and updates symbols to their corresponding vars."
  [tree]
  (prewalk (fn [n]
             ;; This resolves any symbols (mostly for function names)
             ;; into vars.
             (if-let [n-var
                      (and (symbol? n) (not= 'fn n) (get (ns-map *ns*) n))]
               (var-get n-var)
               n))
           tree))

(defmacro defroutes
  "Define routes in Tram.

  Without using `defroutes`, you won't get automatic template resolution in your
  routes. If you don't care to automatically resolve template names, then you
  can use `def` to create routes.

  The purpose of this macro is to add the namespace of your handlers to the
  route data, and to add the var reference of the handler itself to the handler
  data."
  [var-name routes]
  (let [evaluated-routes (replace-symbols-with-vars routes)]
    `(def ~var-name
       ~(map-routes coerce-route-entries-to-specs evaluated-routes))))

(defn tram-router
  "`reitit.http/router` with default options for tram.

  `routes` - vector of routes.
  `options` - map of possible overrides"
  ([routes]
   (tram-router routes {}))
  ([routes options]
   (http/router routes options)))

(import-vars [reitit.ring ring-handler])
