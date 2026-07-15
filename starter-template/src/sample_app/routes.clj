(ns sample-app.routes
  (:require
    [integrant.core :as ig]
    [reitit.ring :as ring]
    [sample-app.concerns.authentication :refer [authentication-interceptor]]
    [sample-app.concerns.http :refer [as-full-page]]
    [sample-app.config :as sys]
    [sample-app.handlers.authentication-handlers :as auth.handlers]
    [sample-app.views.authentication-views]
    [tram.routes :refer [tram-router] :as tr]))

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
  [_ {:keys [routes csrf-secret]}]
  (tram-router routes
               {:data {:coercion     tr/coercion
                       :interceptors [(tr/format-interceptor)
                                      (tr/exception-interceptor)
                                      (tr/wire-format)
                                      authentication-interceptor
                                      (tr/security {:secret csrf-secret})
                                      (tr/render {:page-wrapper
                                                  as-full-page})]}}))
