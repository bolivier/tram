(ns tram.impl.http
  "Namespace for helpers around http.

  Commonly used functions reexported from `tram.routes`, which is preferable for
  external use."
  (:require [clojure.string :as str]
            [tram.html :refer [make-route]]))

(defn htmx-request? [req]
  (some? (get-in req [:headers "hx-request"])))

(defn html-request? [req]
  (str/starts-with? (get-in req [:headers "accept"] "") "text/html"))

(defn- parse-inputs
  "Only used because the inputs to the redirect fns are so weird."
  [[resp-or-route route-or-params only-params]]
  (if (map? resp-or-route)
    {:route  route-or-params
     :params only-params
     :resp   resp-or-route}
    {:route  resp-or-route
     :params route-or-params
     :resp   {}}))

(defn full-redirect
  "Returns a resp for a 303 redirect. Not via htmx, this causes a full page
  reload.

  Can be called in any of these ways:

  (full-redirect :route/name)
  (full-redirect resp :route/name)
  (full-redirect :route/name {:id 2})
  (full-redirect resp :route/name {:id 2})"
  ([route]
   (full-redirect route {}))
  ([resp-or-route route-or-params]
   (full-redirect resp-or-route route-or-params {}))
  ([resp-or-route route-or-params only-params]
   (let [{:keys [route resp params]}
         (parse-inputs [resp-or-route route-or-params only-params])]
     (-> resp
         (assoc :status 303)
         (assoc-in [:headers "location"] (make-route route params))))))

(defn redirect
  "Returns a resp for a htmx redirect. These use a 301 status, and have a htmx
  header to indicate a redirect.

Can be called in any of these ways:

  (redirect :route/name)
  (redirect resp :route/name)
  (redirect :route/name {:id 2})
  (redirect resp :route/name {:id 2})"
  ([route]
   (redirect route {}))
  ([resp-or-route route-or-params]
   (redirect resp-or-route route-or-params {}))
  ([resp-or-route route-or-params only-params]
   (let [{:keys [route resp params]}
         (parse-inputs [resp-or-route route-or-params only-params])]
     (assoc-in (assoc resp :status 301)
       [:headers "hx-redirect"]
       (make-route route params)))))
