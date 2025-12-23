(ns tram.html
  "Functions for dealing with html and hiccup."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :refer [prewalk]]
            [huff2.core :as h]
            [muuntaja.format.core :as mfc]
            [reitit.core :as r]
            [ring.util.codec :refer [form-decode url-encode]]
            [tram.logging :as log]
            [tram.vars :refer [*req*]])
  (:import (java.io OutputStream)))

(defn ->query
  "`q` is a scalar to be prepared for a query string.

  Strip"
  [q]
  (url-encode (cond
                (keyword? q) (name q)
                :else        (str q))))

(defn make-query-string
  "Convert map `m` into a url query-string.

  Interposes ampersands and escapes keys and vals. Only supports scalar values."
  [m]
  (when m
    (->> (for [[k v] m]
           (str (->query k)
                "="
                (->query v)))
         (interpose "&")
         (apply str))))

(defn make-path
  "Convert a keyword name of a route into the route name.

  `router`       - your tram router
  `route-name`   - keyword of the route to convert.  This is usually
                   something like `:route/dashboard`.
  `route-params` - optional params to replace in the url.
                   This is for a route like `/users/:user-id` and you'd pass
                   `{:user-id 1}`"
  ([router route-name]
   (make-path router route-name {}))
  ([router route-name route-params]
   (let [base-path (:path (r/match-by-name router route-name route-params))]
     (when (not base-path)
       (log/event! ::route-not-found
                   {:data {:message      "Could not create path from route"
                           :route-name   route-name
                           :route-params route-params}}))
     (if-let [query-string (make-query-string (:tram.routes/query
                                                route-params))]
       (str base-path "?" query-string)
       base-path))))

(defn make-route
  "Marks a route name as something that the
   hiccup interceptor should convert into a route.

  Ignores strings."
  ([route-name]
   (if (string? route-name)
     route-name
     (make-route route-name
                 nil)))
  ([route-name params]
   [::make route-name params]))

(defn expandable-route-ref?
  "Takes a vector rout reference, like [::make :route/home] and returns if that
  is something we can expand."
  [v]
  (and (vector? v) (= ::make (first v))))

(defn route-name-expander [router node]
  (cond
    (expandable-route-ref? node)
    (let [[_ route-name route-params] node]
      (make-path router route-name route-params))

    ;; recognize routes like `:route.section/foo` as a route keyword.
    (and (keyword? node)
         (= "route"
            (some-> node
                    namespace
                    (str/split #"\.")
                    first)))
    (make-path router node nil)

    :else node))

(defn huff-html-encoder [_]
  (reify
    mfc/EncodeToBytes
    (encode-to-bytes [_ data charset]
      (assert *req* "*req* MUST be bound in encoder")
      (let [router   (:reitit.core/router *req*)
            _ (assert router "router is required in encoder")
            expander (partial route-name-expander router)
            mapper   (fn [[k v]] [k
                                  (if (coll? v)
                                    (prewalk expander
                                             v)
                                    (expander v))])]
        (.getBytes (str (h/html {:allow-raw   true
                                 :attr-mapper mapper}
                                data))
                   ^String charset)))

    mfc/EncodeToOutputStream
    (encode-to-output-stream [_ data _charset]
      (fn [^OutputStream output-stream]
        (let [router   (:retit.core/router *req*)
              _ (assert router "router is required in encoder")
              expander (partial route-name-expander router)
              mapper   (fn [[k v]] [k
                                    (if (coll? v)
                                      (prewalk expander
                                               v)
                                      (expander v))])]
          (spit output-stream
                (io/input-stream (.getBytes (str (h/html {:allow-raw   true
                                                          :attr-mapper mapper}
                                                         data)))))
          (.flush output-stream))))))

(defn form-decoder [_]
  (reify
    mfc/Decode
    (decode [_this data charset]
      (reduce-kv (fn [coll k v] (assoc coll (keyword k) v))
                 {}
                 (form-decode (slurp data) charset)))))

(def html-formatter
  "Muuntaja formatter for html content.

  These are (slightly changed) maps with encoder/decoder fields.

  `encoder` here is wrapped in brackets because muuntaja evaluates a normal fn there."
  (mfc/map->Format {:name    "text/html"
                    :encoder [huff-html-encoder]
                    :return  nil
                    :matches nil}))

(def form-urlencoded-formatter
  "Muuntaja formatter for form-encoded content."
  (mfc/map->Format {:name    "application/x-www-form-urlencoded"
                    :decoder [form-decoder]}))
