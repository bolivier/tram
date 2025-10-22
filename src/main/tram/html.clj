(ns tram.html
  "Functions for dealing with html and hiccup."
  (:require [clojure.java.io :as io]
            [huff.core :as h]
            [muuntaja.core :as m]
            [muuntaja.format.core :as mfc]
            [reitit.core :as r]
            [ring.util.codec :refer [form-decode]])
  (:import (java.io OutputStream)))

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
   (:path (r/match-by-name router route-name route-params))))

(defn make-route
  ([route-name]
   (make-route route-name nil))
  ([route-name params]
   [::make route-name params]))

(defn expandable-route-ref?
  "Takes a vector rout reference, like [::make :route/home] and returns if that
  is something we can expand."
  [v]
  (and (vector? v) (= ::make (first v))))

(defn hiccup-component-expander
  "Given a hiccup vector where the first element is a component fn, invoke that
  with the args to expand it into its result."
  [_ node]
  (if (and (vector? node)
           (fn? (first node)))
    (let [f    (first node)
          args (rest node)]
      (apply f
        args))
    node))

(defn route-name-expander [req node]
  (let [router (:reitit.core/router req)]
    (cond
      (expandable-route-ref? node)
      (let [[_ route-name route-params] node]
        (make-path router route-name route-params))

      (and (keyword? node) (= "route" (namespace node)))
      (make-path router node nil)

      :else node)))

(def expanders
  "List of functions that take a req and a node and return an expanded node, for
  whatever expanded means."
  [hiccup-component-expander route-name-expander])

(defn huff-html-encoder [_]
  (reify
    mfc/EncodeToBytes
    (encode-to-bytes [_ data charset]
      (.getBytes (h/html {:allow-raw true} data) ^String charset))

    mfc/EncodeToOutputStream
    (encode-to-output-stream [_ data _charset]
      (fn [^OutputStream output-stream]
        (spit output-stream
              (io/input-stream (.getBytes (h/html {:allow-raw true} data))))
        (.flush output-stream)))))

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

(defn make-muuntaja-instance
  "make a muuntaja instance with default options.

  Includes an html formatter, a urlencoded formatter, and sets the default
  format tho text/html.

  Options are merged the map fed to `muuntaja.core/create` last."
  ([]
   (make-muuntaja-instance {}))
  ([options]
   (-> m/default-options
       (assoc-in [:formats "text/html"] html-formatter)
       (assoc-in [:formats "application/x-www-form-urlencoded"]
                 form-urlencoded-formatter)
       (assoc :default-format "text/html")
       (merge options)
       (m/create))))
