(ns tram-docs.handlers.example-signal-handlers
  (:require [tram-docs.views.example-signal-views]
            [tram.routes :as tr]))

(defn signals-page [req]
  {:status 200})

(tr/defroutes routes
  [""
   ["/signals"
    {:name :route/examples.signals
     :get  signals-page}]])
