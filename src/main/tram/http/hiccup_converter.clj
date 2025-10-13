(ns tram.http.hiccup-converter
  (:require [hickory.core :as hc]))

(defn html->hiccup
  "Converts a string of html to hiccup.

  Converts as a fragment, not a full page."
  [s]
  (-> s
      hc/parse-fragment
      first
      hc/as-hiccup))
