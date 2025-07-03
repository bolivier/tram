(ns sample-app.handlers.sign-in-handlers
  (:require [tram.core :refer [defroutes tram-router]]
            [tram.test-fixtures :refer [ok-good-handler]]))

(defn sign-in-handler [req]
  {:status 200})

(def dashboard-route
  ["/dashboard"
   ["" {:name :route/dashboard}]
   ["/users/:user-id"
    {:name   :route/user
     :get    :views/show-user
     :post   ok-good-handler
     :patch  {:handler ok-good-handler}
     :put    ok-good-handler
     :delete ok-good-handler}]])

(defroutes routes
  [""
   ["/sign-in"
    {:name :route/sign-in
     :get  sign-in-handler}]
   dashboard-route])

(def router
  (tram-router routes))
