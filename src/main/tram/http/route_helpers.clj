(ns tram.http.route-helpers)

(defn make-route [route-name]
  [::make route-name])

(defn ok
  ([resp]
   (ok resp nil))
  ([resp body]
   (let [resp (assoc resp :status 200)]
     (if body
       (assoc resp
         :body body)
       resp))))

(defn not-found [resp]
  (assoc resp :status 404))

(defn redirect
  "Returns a resp for a 303 redirect."
  [resp route-name]
  (-> resp
      (assoc :status 303)
      (assoc-in [:headers "location"] (make-route route-name))))

(defn bad-request
  "Returns a resp for a 400 bad request."
  [resp]
  (assoc resp :status 400))

(defn not-found
  "Returns a resp for 404 not found."
  [resp]
  (assoc resp :status 404))

(defn hx-redirect
  "Returns a resp for a htmx redirect. These use a 200 status, but have a htmx
  header to indicate a redirect."
  [resp route-name]
  (-> resp
      (assoc :status 200)
      (assoc-in [:headers "hx-redirect"] (make-route route-name))))
