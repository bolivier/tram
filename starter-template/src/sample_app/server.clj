(ns sample-app.server
  (:require [integrant.core :as ig]
            [sample-app.config :as sys]
            [sample-app.routes]
            [org.httpkit.server :as hk-server]
            [reitit.http :as http]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.ring :as ring]))

(defmethod ig/init-key ::sys/app
  [_ {:keys [routes]}]
  (http/ring-handler routes
                     (ring/routes (ring/create-default-handler)
                                  (ring/redirect-trailing-slash-handler))
                     {:executor sieppari/executor}))

(defmethod ig/init-key ::sys/server
  [_ {:keys [app port]}]
  (hk-server/run-server app {:port port}))

(defmethod ig/halt-key! ::sys/server
  [_ server]
  (server))
