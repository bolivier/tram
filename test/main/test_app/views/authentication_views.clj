(ns test-app.views.authentication-views)

(defn layout [children]
  [:div#layout children])

(defn sign-in [_ctx]
  "hello, sign in")

(defn forgot [_locals]
  "forgotten password")

(defn forgot-password [_locals]
  "forgotten password")

(defn my-fn-template [_locals]
  "fn template")

(defn my-keyword-template [_locals]
  "template from keyword")
