(ns tram.http.format
  (:require [clojure.java.io :as io]
            [huff.core :as h]
            [muuntaja.core :as m]
            [muuntaja.format.core :as mfc]
            [ring.util.codec :refer [form-decode]])
  (:import (java.io OutputStream)))

(defn encoder [_]
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
                    :encoder [encoder]
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
