(ns rapid-test.req)

(defn rhizome-request
  "Makes `req` look like one the rhizome runtime drove, rather than a page load."
  [req]
  (assoc-in req [:headers "rhizome-request"] "true"))
