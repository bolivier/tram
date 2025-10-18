(ns rapid-test.req)

(defn htmx-request
  "Makes `req` an htmx event created request. "
  [req]
  (assoc-in req [:headers "hx-request"] "true"))
