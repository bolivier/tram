(ns sample-app.routes
  (:require
    [integrant.core :as ig]
    [reitit.coercion.malli :as rcm]
    [reitit.ring :as ring]
    [sample-app.concerns.authentication :refer [authentication-interceptor]]
    [sample-app.concerns.http :refer [as-full-page]]
    [sample-app.config :as sys]
    [sample-app.handlers.authentication-handlers :as auth.handlers]
    [sample-app.views.authentication-views]
    [tram.routes :refer [tram-router] :as tr]))

(def muuntaja-instance
  (tr/make-muuntaja-instance))

(defmethod ig/init-key ::sys/routes
  [_ _]
  [""
   ["/assets/*" {:get (ring/create-resource-handler)}]
   ["/healthcheck"
    {:name    :route/healthcheck
     :handler (constantly {:status 200
                           :body   "Alive."})}]
   auth.handlers/routes
   ["/dashboard"
    {:name :route/dashboard
     :get  {:handler (fn [_]
                       {:status 200
                        :template
                        #'sample-app.views.authentication-views/dashboard})}}]])

(defmethod ig/init-key ::sys/router
  [_ {:keys [routes]}]
  (tram-router
    routes
    {:data {:muuntaja     muuntaja-instance
            :coercion     rcm/coercion
            :interceptors (concat [(tr/format-interceptor muuntaja-instance)
                                   (tr/wrap-page-interceptor as-full-page)]
                                  tr/default-interceptors
                                  [authentication-interceptor])}}))
