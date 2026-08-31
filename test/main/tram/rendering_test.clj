(ns tram.rendering-test
  (:require [clojure.test :refer [deftest is]]
            [reitit.core :as r]
            [tram.executor :as executor]
            [tram.rendering :as sut]
            [tram.test-fixtures :refer [sample-router]]))

(deftest render-group-ends-with-the-status-interceptor
  (is (= :tram/status (:name (last (sut/render {:page-wrapper identity}))))))

(deftest a-redirect-status-lowers-and-expands-through-the-group
  (let [ctx (executor/run-leaves
              {:request  {:request-method :get
                          :uri       "/x"
                          ::r/router sample-router}
               :response {:status [:redirect :route/dashboard]}}
              [sut/expand-header-routes-interceptor sut/status-interceptor])]
    (is (nil? (:error ctx)))
    (is (= 303 (get-in ctx [:response :status])))
    (is (= "/dashboard" (get-in ctx [:response :headers "location"])))))
