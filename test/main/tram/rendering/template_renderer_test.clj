(ns tram.rendering.template-renderer-test
  (:require [clojure.test :refer [deftest is]]
            [expectations.clojure.test :as e]
            [rapid-test.req :as rt.req]
            [test-app.handlers.authentication-handlers :refer [test-router]]
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

(deftest layout-updates-in-correct-order
  (let [ctx       {:layouts [(fn [body] (* 2 body)) (fn [body] (inc body))]}
        layout-fn (sut/make-root-layout-fn ctx)]
    (is (= 4 (layout-fn 1)))))

(deftest layout-is-not-applied-to-htmx-req
  (let [ctx       {:request (rt.req/htmx-request {})
                   :layouts [(fn [body] (* 2 body)) (fn [body] (inc body))]}
        layout-fn (sut/make-root-layout-fn ctx)]
    (is (= 1 (layout-fn 1)))))
