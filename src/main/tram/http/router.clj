(ns tram.http.router
  (:require [clojure.walk :refer [prewalk]]
            [potemkin :refer [import-vars]]
            [reitit.http :as http]
            [reitit.ring]
            [tram.http.lookup :refer [handlers-ns->views-ns]]
            [tram.utils :refer [evolve]]))

(defn at-route-def?
  "Am I looking at a route definition?

  That is, a map that has a :name keyword in it."
  [node]
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
  "Default handler for a template literal.

  Note: `*ns*` bindings change between executions of the wrapper and the response fn.

  Grabbing the ns from the wrapper function, immediately on invocation, seems to
  be more correct than from the returned handler."
  [template]
  (let [view-ns      (handlers-ns->views-ns *ns*)
        view-name    (name template)
        view-symbol  (symbol (str view-ns "/" view-name))
        view-handler (requiring-resolve view-symbol)]
    ;; TODO move this to custom error interceptor for other kinds of errors
    (if-not view-handler
      (fn [_req]
        {:status 500
         :body   [:div
                  [:div {:style {:background  "#C00"
                                 :font-weight "bold"
                                 :padding     "1rem 2rem"
                                 :font-size   "2rem"
                                 :width       "100%"
                                 :color       "white"}}
                   [:h1 "Missing View function"]]
                  [:p {:style {:padding "1rem 2rem"}}
                   "Expected to find view function at "
                   [:code (str view-symbol)]
                   " but function does not exist."]
                  [:p {:style {:padding "1rem 2rem"}}
                   "Create function "
                   [:code (str view-name)]
                   " in namespace "
                   [:code (str view-ns)]
                   " to remove this error."]]})
      (fn [_req]
        {:status 200
         :body   (view-handler {})}))))

(defn already-evolved?
  "Has this handler like thing already been evolved into a final state?"
  [handler-like]
  (and (:handler handler-like) (:handler-var handler-like)))

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
  [handler-like]
  (if (already-evolved? handler-like)
    handler-like
    (let [handler (if (keyword? handler-like)
                    (default-handler handler-like)
                    (or (:handler handler-like)
                        handler-like))]
      {:handler     handler
       :handler-var (fn->var handler)})))

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
  (let [evaluated-routes (prewalk (fn [n]
                                    (if-let [var (and (symbol? n) (resolve n))]
                                      @var
                                      n))
                                  routes)]
    `(def ~var-name
       ~(prewalk (fn [node]
                   (if (at-route-def? node)
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
