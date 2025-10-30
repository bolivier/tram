(ns tram.impl.http
  "Namespace for helpers around http.

  Commonly used functions reexported from `tram.routes`, which is preferable for
  external use."
  (:require [clojure.string :as str]
            [tram.html :refer [make-route]]))

(def ^:dynamic *current-user*
  "The currently authenticated user.

  Automatically populated in views for a request."
  nil)
(def ^:dynamic *req*
  "The current request.

  Automatically populated in views for a request."
  nil)
(def ^:dynamic *res*
  "The current response.

  Automatically populated in views for a request."
  nil)

(defn htmx-request? [req]
  (some? (get-in req [:headers "hx-request"])))

(defn html-request? [req]
  (str/starts-with? (get-in req [:headers "accept"] "") "text/html"))

(defn full-redirect
  "Returns a resp for a 303 redirect. Not via htmx, this causes a full page
  reload."
  ([route-name]
   (full-redirect {} route-name))
  ([resp route-name]
   (-> resp
       (assoc :status 303)
       (assoc-in [:headers "location"] route-name))))

(defn redirect
  "Returns a resp for a htmx redirect. These use a 301 status, and have a htmx
  header to indicate a redirect."
  ([route]
   (redirect {} route))
  ([resp route]
   (-> resp
       (assoc :status 301)
       (assoc-in [:headers "hx-redirect"] route))))
