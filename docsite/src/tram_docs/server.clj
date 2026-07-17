(ns tram-docs.server
  (:require [integrant.core :as ig]
            [org.httpkit.server :as hk-server]
            [reitit.http :as http]
            [reitit.interceptor.sieppari :as sieppari]
            [reitit.ring :as ring]
            [tram-docs.config :as sys]
            [tram-docs.routes]))

(defmethod ig/init-key ::sys/app
  [_ {:keys [router]}]
  (http/ring-handler router
                     (ring/routes (ring/create-default-handler)
                                  (ring/redirect-trailing-slash-handler))
                     {:executor sieppari/executor}))

(defmethod ig/init-key ::sys/server
  [_ {:keys [app port]}]
  (hk-server/run-server app {:port port}))

(defmethod ig/halt-key! ::sys/server
  [_ server]
  (server))
