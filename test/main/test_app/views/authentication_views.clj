(ns test-app.views.authentication-views)

(defn layout [children]
  [:div#layout children])

(defn sign-in [ctx]
  "hello, sign in")

(defn forgot [locals]
  "forgotten password")

(defn forgot-password [locals]
  "forgotten password")

(defn my-fn-template [locals]
  "fn template")

(defn my-keyword-template [locals]
  "template from keyword")
