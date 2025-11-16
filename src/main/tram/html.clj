(ns tram.html
  "Functions for dealing with html and hiccup."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk]
            [huff2.core :as h]
            [malli.core :as m]
            [malli.transform :as mt]
            [muuntaja.format.core :as mfc]
            [reitit.core :as r]
            [ring.util.codec :refer [form-decode]]
            [tram.errors :as te])
  (:import (java.io OutputStream)))

(defn make-path
  "Convert a keyword name of a route into the route name.

  `router`       - your tram router
  `route-name`   - keyword of the route to convert.  This is usually
                   something like `:route/dashboard`.
  `route-params` - optional params to replace in the url.
                   This is for a route like `/users/:user-id` and you'd pass
                   `{:user-id 1}`.
                   Query params are passed under the key :tram.routing/query.
                   They are validated only against the `:get` request for
                   the named route."
  ([router route-name]
   (make-path router route-name {}))
  ([router route-name route-params]
   (let [match        (r/match-by-name router route-name route-params)
         query-params (:tram.routing/query route-params)
         path         (:path match)]
     (if-not query-params
       path
       (let [query-param-schema (get-in match [:data :get :parameters :query])]
         (when-not (m/validate query-param-schema query-params)
           (throw (ex-info "Invalid query params in make-path"
                           {:route-name route-name
                            :route-params route-params
                            :path path
                            :match match})))

         (let [url-param-string (->> (m/coerce query-param-schema
                                               query-params
                                               mt/strip-extra-keys-transformer)
                                     (map (fn [[k v]]
                                            (str (name k) "=" (name v))))
                                     (str/join "&"))]
           (str path "?" url-param-string)))))))

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

    (and (keyword? node) (= "route" (namespace node)))
    (make-path router node nil)

    :else node))

(defn huff-html-encoder [{:keys [router]}]
  (let [expander (partial route-name-expander router)
        mapper   (fn [[k v]] [k
                              (if (coll? v)
                                (clojure.walk/prewalk expander
                                                      v)
                                (expander v))])]
    (reify
      mfc/EncodeToBytes
      (encode-to-bytes [_ data charset]
        (.getBytes (str (h/html {:allow-raw   true
                                 :attr-mapper mapper}
                                data))
                   ^String charset))

      mfc/EncodeToOutputStream
      (encode-to-output-stream [_ data _charset]
        (fn [^OutputStream output-stream]
          (spit output-stream
                (io/input-stream (.getBytes (str (h/html {:allow-raw true}
                                                         data)))))
          (.flush output-stream))))))

(defn form-decoder [_]
  (reify
    mfc/Decode
    (decode [_this data charset]
      (reduce-kv (fn [coll k v] (assoc coll (keyword k) v))
                 {}
                 (form-decode (slurp data) charset)))))

(defn make-html-formatter
  "Muuntaja formatter for html content.

  These are (slightly changed) maps with encoder/decoder fields.

  `encoder` here is wrapped in brackets because muuntaja evaluates a normal fn there."
  [routes]
  (mfc/map->Format {:name    "text/html"
                    :encoder [huff-html-encoder {:router (reitit.core/router
                                                          routes)}]
                    :return  nil
                    :matches nil}))

(def form-urlencoded-formatter
  "Muuntaja formatter for form-encoded content."
  (mfc/map->Format {:name    "application/x-www-form-urlencoded"
                    :decoder [form-decoder]}))
