(ns test-app.handlers.authentication-handlers
  (:require [reitit.core]
            [reitit.http :refer [router]]
            [test-app.views.authentication-views :as view]
            [tram.core]))

(defn sign-in [req]
  {:status 200})

(defn forgot [req]
  {:status 200})

(tram.core/defroutes routes
  [""
   {:layout view/layout}
   ["/sign-in"
    {:get  sign-in
     :name :route/sign-in}]
   ["/forgot-password"
    {:get  :view/forgot-password
     :name :route/forgot-password
     :post forgot}]
   ["/healthcheck"
    {:post (constantly {:status 200})
     :get  (fn [_] {:status 200})
     :name :route/healthcheck}]])

(def test-router
  (router routes))
