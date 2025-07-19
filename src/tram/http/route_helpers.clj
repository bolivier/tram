(ns tram.http.route-helpers)

(defn expandable-route-ref?
  "Takes a vector rout reference, like [::make :route/home] and returns if that
  is something we can expand."
  [v]
  (and (vector? v) (= ::make (first v))))

(defn make-route
  ([route-name]
   (make-route route-name nil))
  ([route-name params]
   [::make route-name params]))

(defn ok
  ([]
   (ok {}))
  ([resp]
   (ok resp nil))
  ([resp body]
   (let [resp (assoc resp :status 200)]
     (if body
       (assoc resp
         :body body)
       resp))))

(defn redirect
  "Returns a resp for a 303 redirect."
  ([route-name]
   (redirect {} route-name))
  ([resp route-name]
   (-> resp
       (assoc :status 303)
       (assoc-in [:headers "location"] (make-route route-name)))))

(defn bad-request
  "Returns a resp for a 400 bad request."
  ([]
   (bad-request {}))
  ([resp]
   (assoc resp :status 400)))

(defn not-found
  "Returns a resp for 404 not found."
  ([]
   (not-found {}))
  ([resp]
   (assoc resp :status 404)))

(defn hx-redirect
  "Returns a resp for a htmx redirect. These use a 200 status, but have a htmx
  header to indicate a redirect."
  ([route]
   (hx-redirect {} route))
  ([resp route]
   (-> resp
       (assoc :status 301)
       (assoc :body "")
       (assoc-in [:headers "hx-redirect"] route))))
