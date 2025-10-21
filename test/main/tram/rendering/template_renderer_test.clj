(ns tram.rendering.template-renderer-test
  (:require [clojure.test :refer [deftest is]]
            [rapid-test.req :as rt.req]
            [test-app.handlers.authentication-handlers :refer [test-router]]
            [test-app.views.authentication-views :as views]
            [tram.rendering.template-renderer :as sut]))

(deftest rendering-nil-template-test
  (let [request {:uri "/sign-in"
                 :request-method :get
                 :reitit.core/router test-router}
        body    (-> {:request request}
                    sut/render
                    (get-in [:response :body]))]
    (is (= (views/sign-in nil) body))))

(deftest rendering-keyword-template-test
  (let [match   (-> test-router
                    (reitit.core/match-by-name :route/forgot-password))
        handler (get-in match [:data :get :handler])
        request {:uri "/forgot-password"
                 :request-method :get
                 :reitit.core/match match
                 :reitit.core/router test-router}
        ctx     (sut/render {:request  request
                             :response (handler request)})]
    (is (= (views/forgot-password nil) (:body (:response ctx))))))

(deftest rendering-explicit-function-template-test
  (let [match   (-> test-router
                    (reitit.core/match-by-name :route/fn))
        handler (get-in match [:data :get :handler])
        request {:uri "/templated/function"
                 :request-method :get
                 :reitit.core/match match
                 :reitit.core/router test-router}
        ctx     (sut/render {:request  request
                             :response (handler request)})]
    (is (= (views/my-fn-template nil) (:body (:response ctx))))))

(deftest rendering-explicit-keyword-template-test
  (let [match   (-> test-router
                    (reitit.core/match-by-name :route/keyword))
        handler (get-in match [:data :get :handler])
        request {:uri "/templated/keyword"
                 :request-method :get
                 :reitit.core/match match
                 :reitit.core/router test-router}
        ctx     (sut/render {:request  request
                             :response (handler request)})]
    (is (= (views/my-keyword-template nil) (:body (:response ctx))))))

(deftest rendering-missing-template-test
  (let [match   (-> test-router
                    (reitit.core/match-by-name :route/sign-out))
        handler (get-in match [:data :get :handler])
        request {:uri "/sign-out"
                 :request-method :get
                 :reitit.core/match match
                 :reitit.core/router test-router}]
    (is (thrown-match? clojure.lang.ExceptionInfo
                       {:uri           "/sign-out"
                        :template-name "<nil>"
                        :error         :no-template}
                       (sut/render {:request  request
                                    :response (handler request)})))))

(deftest layout-updates-in-correct-order
  (let [ctx       {:layouts [(fn [body] (* 2 body)) (fn [body] (inc body))]}
        layout-fn (sut/make-root-layout-fn ctx)]
    (is (= 4 (layout-fn 1)))))

(deftest layout-is-not-applied-to-htmx-req
  (let [ctx       {:request (rt.req/htmx-request {})
                   :layouts [(fn [body] (* 2 body)) (fn [body] (inc body))]}
        layout-fn (sut/make-root-layout-fn ctx)]
    (is (= 1 (layout-fn 1)))))
