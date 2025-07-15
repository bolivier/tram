(ns tram.rendering.template-renderer-test
  (:require [expectations.clojure.test :as e]
            [tram.rendering.template-renderer :as sut]))

(ns sample-app.handlers.authentication-handlers
  (:require [reitit.core]
            [reitit.http :refer [router]]
            [tram.core]))

(defn sign-in [req]
  {:status 200})

(tram.core/defroutes routes
  ["/sign-in"
   {:name :route/sign-in
    :get  sign-in}])

(def sample-router
  (router routes))

(ns sample-app.views.authentication-views)

(defn sign-in [ctx]
  "hello")

(ns tram.rendering.template-renderer-test
  (:require
    [sample-app.handlers.authentication-handlers :refer [sample-router sign-in]]
    [sample-app.views.authentication-views]))

(e/defexpect renderer
  (let [request {:uri "/sign-in"
                 :request-method :get
                 :reitit.core/router sample-router}
        ctx     (sut/render {:request  request
                             :response (sign-in request)})]
    (e/expect "hello" (:body (:response ctx)))))
