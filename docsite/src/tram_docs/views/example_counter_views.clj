(ns tram-docs.views.example-counter-views
  (:require [rhizome.core :as rz]
            [tram-docs.components.examples :refer [demo-card]]
            [tram.routes :as tr]))


(defn global-button [count]
  [:button#global-counter.btn.primary {::rz/click
                                       {:do :http/post
                                        :http/url
                                        "/rhizome/examples/counter/global"}}
   (str "Increment Global: " count)])

(defn user-button [count]
  [:button#user-counter.btn.primary {::rz/click
                                     {:do :http/post
                                      :http/url
                                      "/rhizome/examples/counter/user"}}
   (str "Increment User: " count)])

(defn counter-example [{:keys [global-count user-count]}]
  [demo-card [global-button global-count] [user-button user-count]])
