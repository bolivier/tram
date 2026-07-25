(ns tram-docs.concerns.http
  (:require [tram.routes :as tr]))

(defn as-full-page
  ([body]
   (as-full-page "Sample Tram App" body))
  ([title body]
   [:html
    [:head
     [:title title]

     [:script {:src "/assets/js/rhizome.js"}]

     [:link {:rel  :stylesheet
             :href "/assets/css/index.css"}]

     #_[:link {:rel  :stylesheet
               :href "/assets/index.css"}]]
    [:body
     body]]))
