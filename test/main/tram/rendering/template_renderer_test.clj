(ns tram.rendering.template-renderer-test
  (:require [clojure.test :refer [deftest is]]
            [rapid-test.req :as rt.req]
            [reitit.core :as r]
            [test-app.handlers.authentication-handlers :refer [test-router]]
            [test-app.views.authentication-views :as views]
            [tram.rendering.template-renderer :as sut]))

(deftest rendering-nil-template-test
  (let [request {:uri            "/sign-in"
                 :request-method :get
                 ::r/match       (r/match-by-name test-router :route/sign-in)
                 ::r/router      test-router}
        body    (-> {:request request}
                    sut/render
                    (get-in [:response :body]))]
    (is (= (views/sign-in nil) body))))

(deftest rendering-keyword-template-test
  (let [match   (-> test-router
                    (reitit.core/match-by-name :route/forgot-password))
        handler (get-in match [:data :get :handler])
        request {:uri            "/forgot-password"
                 :request-method :get
                 ::r/match       match
                 ::r/router      test-router}
        ctx     (sut/render {:request  request
                             :response (handler request)})]
    (is (= (views/forgot-password nil) (:body (:response ctx))))))

(deftest rendering-explicit-function-template-test
  (let [match   (-> test-router
                    (reitit.core/match-by-name :route/fn))
        handler (get-in match [:data :get :handler])
        request {:uri            "/templated/function"
                 :request-method :get
                 ::r/match       match
                 ::r/router      test-router}
        ctx     (sut/render {:request  request
                             :response (handler request)})]
    (is (= (views/my-fn-template nil) (:body (:response ctx))))))

(deftest rendering-explicit-keyword-template-test
  (let [match   (-> test-router
                    (reitit.core/match-by-name :route/keyword))
        handler (get-in match [:data :get :handler])
        request {:uri            "/templated/keyword"
                 :request-method :get
                 ::r/match       match
                 ::r/router      test-router}
        ctx     (sut/render {:request  request
                             :response (handler request)})]
    (is (= (views/my-keyword-template nil) (:body (:response ctx))))))

(deftest rendering-missing-template-test
  (let [match   (-> test-router
                    (reitit.core/match-by-name :route/sign-out))
        handler (get-in match [:data :get :handler])
        request {:uri            "/sign-out"
                 :request-method :get
                 ::r/match       match
                 ::r/router      test-router}]
    (is (thrown-match? clojure.lang.ExceptionInfo
                       {:uri "/sign-out"
                        :template 'test-app.views.authentication-views/sign-out
                        :template-name "sign-out"
                        :error :no-template}
                       (sut/render {:request  request
                                    :response (handler request)})))))

(deftest view-written-after-its-route-was-compiled-still-renders
  (let [match   (r/match-by-name test-router :route/sign-out)
        request {:uri      "/sign-out"
                 :request-method :get
                 ::r/match match}]
    (intern 'test-app.views.authentication-views
            'sign-out
            (fn [_] [:div "written later"]))
    (try
      (is (= [:div "written later"]
             (get-in (sut/render {:request  request
                                  :response {:status 200}})
                     [:response :body])))
      (finally (ns-unmap 'test-app.views.authentication-views 'sign-out)))))

(deftest keyword-template-ns-derivation-only-converts-trailing-segments
  (let [ctx {:request {::r/match {:data
                                  {:namespace
                                   "handler-co.handlers.handler-utils"}}}}]
    (is (= "handler-co.views.handler-utils"
           (sut/get-namespace :view/anything ctx)))))

(deftest layout-updates-in-correct-order
  (let [ctx       {:layouts [(fn [body] (* 2 body)) (fn [body] (inc body))]}
        layout-fn (sut/make-root-layout-fn ctx)]
    (is (= 4 (layout-fn 1)))))

(deftest layout-is-not-applied-to-htmx-req
  (let [ctx       {:request (rt.req/htmx-request {})
                   :layouts [(fn [body] (* 2 body)) (fn [body] (inc body))]}
        layout-fn (sut/make-root-layout-fn ctx)]
    (is (= 1 (layout-fn 1)))))
