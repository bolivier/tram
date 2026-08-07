(ns tram-docs.views.example-signal-views
  (:require [rhizome.core :as rz]
            [tram-docs.components.examples :refer [demo-card]]))

(defn signals-page
  "Two signals and the three bindings that read them. Nothing here talks to the
  server, so every update is the store driving the dom."
  [_locals]
  [demo-card
   [:div
    [:h3 "Signal store"]
    [:pre {::rz/debug true}]]
   [:div
    [:h3.text-xl.font-semibold "Text"]
    [:p "Bound to: " [:span {::rz/text :example-text}]]
    [:input.input {::rz/bind [:example-text "text"]}]]
   [:div
    [:h3 "Show"]
    [:p
     [:span {::rz/show :example-visible?}
      "Content is visible"]]
    [:label
     [:input {::rz/bind [:example-visible? true]
              :type     :checkbox}]
     "Show content"]]])
