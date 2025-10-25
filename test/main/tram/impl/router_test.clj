(ns tram.impl.router_test
  (:require [clojure.test :refer [deftest is]]
            [matcher-combinators.test]
            [test-app.views.authentication-views :as views]
            [tram.impl.router :as sut]))

(deftest layout-interceptor
  (let [layout-fn (fn [children] [:div#layout children])
        ctx       (apply (:enter (sut/layout-interceptor layout-fn)) [{}])]
    (is (match? {:layouts [layout-fn]} ctx)
        "Layout interceptor did not add layout-fn to :layouts key.")))

(deftest coerce-route-entries-to-specs-test
  (is (match? {:interceptors [{:name  :tram.impl.router/layout-interceptor
                               :enter fn?}]
               :layout       views/layout}
              (sut/coerce-route-entries-to-specs {:layout views/layout}))))
