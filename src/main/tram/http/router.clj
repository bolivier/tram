(ns tram.http.router
  (:require [clojure.walk :refer [prewalk]]
            [potemkin :refer [import-vars]]
            [reitit.coercion.malli :as rcm]
            [reitit.http :as http]
            [reitit.http.coercion
             :refer
             [coerce-request-interceptor coerce-response-interceptor]]
            [reitit.http.interceptors.multipart :refer [multipart-interceptor]]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.parameters :as rhip]
            [reitit.ring]
            [tram.http.format :refer [make-muuntaja-instance]]
            [tram.http.interceptors
             :refer
             [as-page-interceptor
              expand-hiccup-interceptor
              inject-route-name
              render-template-interceptor]]
            [tram.utils :refer [evolve]]))


(defn at-route-def?
  "Am di looking at a route definition?

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

(defn handler-evolver
  "Evolve a handler.  Receives either a handler fn or a map with a handler key.

  First convert the handler fn into a map like {:handler val}. Then find the var
  that handler fn is stored in, if any, and add that to the map under
  `:handler-var`."
  [val]
  (let [handler (or (:handler val) val)]
    {:handler     handler
     :handler-var (fn->var handler)}))

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
                                      (if (fn? @var)
                                        var
                                        @var)
                                      n))
                                  routes)]
    `(def ~var-name
       ~(prewalk (fn [node]
                   (if (at-route-def? node)
                     (evolve http-verb-evolutions
                             (assoc node
                               :namespace (str *ns*)))
                     node))
                 evaluated-routes))))
(defn tram-router
  "`reitit.http/router` with default options for tram.

  `routes` - vector of routes.
  `options` - map of possible overrides"
  ([routes]
   (tram-router routes {}))
  ([routes options]
   (let [{:keys [muuntaja-instance authentication-interceptor]
          :or   {muuntaja-instance (make-muuntaja-instance)}}
         options]
     (http/router routes
                  {:data {:muuntaja     muuntaja-instance
                          :coercion     rcm/coercion
                          :interceptors [(exception-interceptor)
                                         authentication-interceptor
                                         inject-route-name
                                         (muuntaja/format-interceptor
                                           muuntaja-instance)
                                         (multipart-interceptor)
                                         expand-hiccup-interceptor
                                         as-page-interceptor
                                         (coerce-request-interceptor)
                                         (coerce-response-interceptor)
                                         (rhip/parameters-interceptor)
                                         render-template-interceptor]}}))))

(import-vars [reitit.ring ring-handler])
