(ns tram-docs.handlers.example-signal-handlers
  (:require [rhizome.core :as rz]
            [tram.routes :as tr]))

(defn signals-page [req]
  {:status 200
   :hiccup [:div
            [:h3 "Basic Signal"]
            [:input.input {::rz/bind [:example-text "text"]}]
            [:div.signal-example-space {::rz/text :example-text}]]})

(tr/defroutes routes
  [""
   ["/signals"
    {:name :route/examples.signals
     :get  signals-page}]])
