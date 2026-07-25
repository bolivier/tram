(ns rapid-test.req)

(defn htmx-request
  "Makes `req` an htmx event created request. "
  [req]
  (assoc-in req [:headers "hx-request"] "true"))

(defn rhizome-request
  "Makes `req` look like one the rhizome runtime drove, rather than a page load."
  [req]
  (assoc-in req [:headers "rhizome-request"] "true"))
