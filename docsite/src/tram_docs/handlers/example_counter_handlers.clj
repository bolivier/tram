(ns tram-docs.handlers.example-counter-handlers
  (:require [tram-docs.views.example-counter-views :as v]
            [tram.routes :as tr]))

(defonce global
  (atom 420))
(defonce user
  (atom 67))

(defn click-global [req]
  (swap! global inc)
  {:status 200
   :hiccup [:<> [v/global-button @global]]})

(defn click-user [req]
  (swap! user inc)
  (swap! global inc)
  {:status 200
   :hiccup [:<>
            [v/user-button @user]
            [v/global-button @global]]})

(defn counter-example [req]
  {:status 200
   :locals {:user-count   @user
            :global-count @global}})

(tr/defroutes routes
  ["/counter"
   [""
    {:name :route/examples.counter
     :get  counter-example}]
   ["/global"
    {:name :route/examples.counter.global
     :post click-global}]
   ["/user"
    {:name :route/examples.counter.user
     :post click-user}]])
