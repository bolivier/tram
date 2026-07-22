(ns tram-docs.views.examples-views
  (:require [rhizome.core :as rz]
            [tram.routes :as tr]))

(defn global-button [count]
  [:button#global-counter.btn.primary {::rz/on
                                       {:op :http/post
                                        :on :event/click
                                        :http/url
                                        "/rhizome/examples/counter/global"}}
   (str "Increment Global: " count)])

(defn user-button [count]
  [:button#user-counter.btn.primary {::rz/on {:op :http/post
                                              :on :event/click
                                              :http/url
                                              "/rhizome/examples/counter/user"}}
   (str "Increment User: " count)])

(defn counter-example [{:keys [global-count user-count]}]
  [:div {::rz/on {:op       :http/get
                  :http/url "/events"
                  :ident    [:this]}}
   [global-button global-count]
   [user-button user-count]])
