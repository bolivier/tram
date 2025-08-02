(ns sample-app.routes
  (:require
    [integrant.core :as ig]
    [reitit.coercion.malli :as rcm]
    [reitit.ring :as ring]
    [sample-app.concerns.authentication :refer [authentication-interceptor]]
    [sample-app.concerns.http :refer [as-full-page]]
    [sample-app.config :as sys]
    [sample-app.handlers.authentication-handlers :as auth.handlers]
    [sample-app.http.utils :refer [redirect-to-home-handler]]
    [sample-app.views.authentication-views]
    [tram.http.format :refer (make-muuntaja-instance)]
    [tram.http.interceptors :as tram.interceptors]
    [tram.http.router :refer [tram-router]]))

(def muuntaja-instance
  (make-muuntaja-instance))

(defmethod ig/init-key ::sys/routes
  [_ _]
  (tram-router
    [""
     ["/assets/*" {:get (ring/create-resource-handler)}]
     ["/" {:get redirect-to-home-handler}]
     ["/healthcheck"
      {:name    :route/home
       :handler (constantly {:status 200
                             :body   "Alive."})}]
     auth.handlers/routes
     ["/dashboard"
      {:name :route/dashboard
       :get  {:handler
              (fn [_]
                {:status 200
                 :template
                 #'sample-app.views.authentication-views/dashboard})}}]]
    {:data {:muuntaja     muuntaja-instance
            :coercion     rcm/coercion
            :interceptors (into []
                                (flatten [(tram.interceptors/format-interceptor
                                            muuntaja-instance)
                                          (tram.interceptors/as-page-interceptor
                                            as-full-page)
                                          tram.interceptors/default-interceptors
                                          authentication-interceptor]))}}))
