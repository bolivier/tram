(ns tram.rendering.template-renderer-test
  (:require
    [expectations.clojure.test :as e]
    [test-app.handlers.authentication-handlers :refer [routes test-router]]
    [test-app.views.authentication-views :as views]
    [tram.rendering.template-renderer :as sut]))

(e/defexpect renderer
  (e/expecting "nil template rendering"
               (let [request {:uri "/sign-in"
                              :request-method :get
                              :reitit.core/router test-router}
                     ctx     (sut/render {:request  request
                                          :response {}})]
                 (e/expect (views/sign-in nil) (:body (:response ctx)))))
  (e/expecting
    "keyword template rendering"
    (let [match   (-> test-router
                      (reitit.core/match-by-name :route/forgot-password))
          handler (-> test-router
                      (reitit.core/match-by-name :route/forgot-password)
                      :data
                      :get
                      :handler)
          request {:uri "/forgot-password"
                   :request-method :get
                   :reitit.core/match match
                   :reitit.core/router test-router}
          ctx     (sut/render {:request  request
                               :response (handler request)})]
      (e/expect (views/forgot-password nil) (:body (:response ctx))))))
