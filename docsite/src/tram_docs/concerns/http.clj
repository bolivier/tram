(ns tram-docs.concerns.http
  (:require [tram.routes :as tr]))

(defn as-full-page
  ([body]
   (as-full-page "Sample Tram App" body))
  ([title body]
   [:html
    [:head
     [:title title]

     [:link {:rel  :stylesheet
             :href "/assets/index.css"}]
     (tr/csrf-meta-tag)]
    [:body
     body]]))
