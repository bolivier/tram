(ns tram.impl.router-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test]
            [test-app.views.authentication-views :as views]
            [tram.impl.router :as sut]))

(deftest layout-interceptor
  (let [layout-fn (fn [children] [:div#layout children])
        ctx       (apply (:enter (sut/layout-interceptor layout-fn)) [{}])]
    (is (match? {:layouts [layout-fn]} ctx)
        "Layout interceptor did not add layout-fn to :layouts key.")))

(deftest coerce-layout-test
  ;; Since these operate on macro args, they will be called in the handlers
  ;; ns
  (binding [*ns* (the-ns 'test-app.handlers.authentication-handlers)]
    (testing "Using a function/symbol"
      (is (match?
            {:interceptors ['(tram.impl.router/layout-interceptor views/layout)]
             :layout       'views/layout}
            (sut/coerce-route-entries-to-specs '{:layout views/layout}))))
    (testing "Using a keyword"
      (is (match?
            {:interceptors [`(tram.impl.router/layout-interceptor views/layout)]
             :layout       :view/layout}
            (binding [*ns* (the-ns 'test-app.handlers.authentication-handlers)]
              (sut/coerce-route-entries-to-specs '{:layout :view/layout})))))))
