(ns tram.html
  "Functions for dealing with html and hiccup."
  (:require [clojure.string :as str]
            [clojure.walk :refer [prewalk]]
            [muuntaja.format.core :as mfc]
            [reitit.core :as r]
            [rhizome.html :as h]
            [ring.util.codec :refer [form-decode url-encode]]
            [tram.logging :as log]
            [tram.vars :refer [*req*]])
  (:import (java.io OutputStream)))

(defn ->query
  "`q` is a scalar to be prepared for a query string."
  [q]
  (url-encode (cond
                (keyword? q) (name q)
                :else        (str q))))

(defn make-query-string
  "Convert map `m` into a url query-string.

  Interposes ampersands and escapes keys and vals. Only supports scalar values."
  [m]
  (when (seq m)
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
     (if-not base-path
       (log/event! ::route-not-found
                   {:data {:message      "Could not create path from route"
                           :route-name   route-name
                           :route-params route-params}})
       (if-let [query-string (make-query-string (:tram.routes/query
                                                  route-params))]
         (str base-path "?" query-string)
         base-path)))))

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
      (log/event! ::expanding-roue
                  {:data {:route-name   route-name
                          :route-params route-params
                          :node         node}})
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

;; Not the best extension mechanism, per-keyword.
;; Need to figure out if I wanna do something like attr-mapper or no.
(defmethod h/emit-attr :rhizome.core/on
  [append! key value]
  (append! (h/stringify key) "=\"")
  (binding [*print-namespace-maps* false]
    (h/maybe-escape-html
      append!
      (pr-str
        (if *req*
          (let [router   (:reitit.core/router *req*)
                expander (partial route-name-expander
                                  router)
                mapper   (fn [v]
                           (prewalk expander
                                    v))]
            (mapper value))
          (do (log/event!
                ::processing-rz-on-without-req
                {:data {:message
                        "Could not find *req* while processing :rhizome.core/on"

                        :key key
                        :value value}})
              value)))))
  (append! "\""))

(defmethod h/emit-attr :rhizome.core/text
  [append! key value]
  (append! (h/stringify key) "=\"")
  (append! (str value))
  (append! "\""))

(defmethod h/emit-attr :href
  [append! key value]
  (append! (h/stringify key) "=\"")
  (append! (if *req*
             (let [router (:reitit.core/router *req*)]
               (route-name-expander router
                                    value))
             (do (log/event!
                   ::processing-href-without-req
                   {:data
                    {:message "Could not find *req* while processing :href"
                     :value   value}})
                 value)))
  (append! "\""))

(defn huff-html-encoder [_]
  (reify
    mfc/EncodeToBytes
    (encode-to-bytes [_ data charset]
      (.getBytes (str (h/html {:allow-raw true} data)) ^String charset))

    mfc/EncodeToOutputStream
    (encode-to-output-stream [_ data charset]
      (fn [^OutputStream output-stream]
        (.write output-stream
                (.getBytes (str (h/html {:allow-raw true} data))
                           ^String charset))
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
