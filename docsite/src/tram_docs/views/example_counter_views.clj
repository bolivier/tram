(ns tram-docs.views.example-counter-views
  (:require [rhizome.core :as rz]
            [tram-docs.components.examples :refer [demo-card]]))

(defn global-button [count]
  [:button#global-counter.btn.primary {::rz/click
                                       {:do :http/post
                                        :http/url
                                        :route/examples.counter.global}}
   (str "Increment Global: " count)])

(defn user-button [count]
  [:button#user-counter.btn.primary {::rz/click {:do :http/post
                                                 :http/url
                                                 :route/examples.counter.user}}
   (str "Increment User: " count)])

(defn counter-example
  "The card opens the stream, and the stream is the only thing that moves a
  button. A click tells the server, and every card watching hears about it."
  [{:keys [global-count user-count]}]
  [demo-card {::rz/load {:do       :http/get
                         :http/url :route/examples.counter.stream}}
   [global-button global-count]
   [user-button user-count]])
