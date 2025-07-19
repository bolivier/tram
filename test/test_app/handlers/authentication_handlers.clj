(ns test-app.handlers.authentication-handlers
  (:require [reitit.core]
            [reitit.http :refer [router]]
            [tram.core]))

(defn sign-in [req]
  {:status 200})

(defn forgot [req]
  {:status 200})

(tram.core/defroutes routes
  [["/sign-in"
    {:name :route/sign-in
     :get  sign-in}]
   ["/forgot-password"
    {:name :route/forgot-password
     :get  :view/forgot-password
     :post forgot}]])

(def test-router
  (router routes))
