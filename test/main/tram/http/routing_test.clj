(ns tram.http.routing-test
  (:require [clojure.test :refer [deftest is testing]]
            [tram.http.routing :as sut]
            [tram.test-fixtures :refer [sample-router]]))

(deftest make-simple-path
  (is (= "/dashboard" (sut/make-path sample-router :route/dashboard))))

(deftest makes-path-with-params
  (testing "simple param"
    (is (= "/dashboard/users/1"
           (sut/make-path sample-router :route/user {:user-id 1}))))
  (testing "making path has no type requirements"
    (is (= "/dashboard/users/foobar"
           (sut/make-path sample-router :route/user {:user-id "foobar"})))
    (is (= "/dashboard/users/foobar"
           (sut/make-path sample-router :route/user {:user-id :foobar})))))

(deftest nil-for-non-path
  (is (= nil (sut/make-path sample-router :route/user))))
