(ns test-app.handlers.authentication-handlers
  (:require [reitit.core]
            [reitit.http :refer [router]]
            [test-app.views.authentication-views :as views]
            [tram.core]))

(defn sign-out [req]
  {:status 200})

(defn sign-in [req]
  {:status 200})

(defn forgot [req]
  {:status 200})

(defn keyword-template [req]
  {:status   200
   :template :view/my-keyword-template})

(defn fn-template [req]
  {:status   200
   :template views/my-fn-template})

(tram.core/defroutes routes
  [""
   {:layout views/layout}
   [""
    ["/sign-in"
     {:get  sign-in
      :name :route/sign-in}]
    ["/sign-out"
     {:get  sign-out
      :name :route/sign-out}]]
   ["/forgot-password"
    {:get          :view/forgot-password
     :interceptors [{:name  :identity
                     :enter identity}]
     :name         :route/forgot-password
     :post         {:handler forgot}}]
   ["/healthcheck"
    {:post (constantly {:status 200})
     :get  (fn [_] {:status 200})
     :name :route/healthcheck}]
   ["/templated"
    ["/keyword"
     {:name :route/keyword
      :get  keyword-template}]
    ["/function"
     {:name :route/fn
      :get  fn-template}]]])

(def test-router
  (router routes))
