(ns tram-docs.handlers.examples-handlers
  (:require [tram.routes :as tr]))

(defn counter-example [req]
  {:status 200
   :body   [:div "counter-example"]})

(tr/defroutes routes
  ["/examples"
   ["/counter"
    {:name :route/examples.counter
     :get  counter-example}]])
