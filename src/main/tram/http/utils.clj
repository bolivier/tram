(ns tram.http.utils
  (:require [clojure.string :as str]))

(defn htmx-request? [req]
  (some? (get-in req [:headers "hx-request"])))

(defn html-request? [req]
  (str/starts-with? (get-in req [:headers "accept"] "") "text/html"))
