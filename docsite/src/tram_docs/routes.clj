(ns tram-docs.routes
  (:require [integrant.core :as ig]
            [reitit.ring :as ring]
            [tram-docs.concerns.http :refer [as-full-page]]
            [tram-docs.config :as sys]
            [tram-docs.handlers.examples-handlers :as examples.handlers]
            [tram-docs.handlers.welcome-handlers :as welcome.handlers]
            [tram-docs.views.welcome-views]
            [tram.routes :refer [tram-router] :as tr]))

(defmethod ig/init-key ::sys/routes
  [_ _]
  [""
   ["/assets/*" {:get (ring/create-resource-handler)}]
   ["/healthcheck"
    {:name    :route/healthcheck
     :handler (constantly {:status 200
                           :body   "Alive."})}]
   ["/rhizome" examples.handlers/routes]
   welcome.handlers/routes])

(defmethod ig/init-key ::sys/router
  [_ {:keys [routes csrf-secret]}]
  (tram-router routes
               {:data {:coercion     tr/coercion
                       :interceptors [(tr/format-interceptor)
                                      (tr/exception-interceptor)
                                      (tr/wire-format)
                                      #_(tr/security {:secret csrf-secret})
                                      (tr/render {:page-wrapper
                                                  as-full-page})]}}))
