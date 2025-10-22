(ns tram.html-test
  (:require [clojure.test :refer [deftest is]]
            [tram.html :as sut]
            [tram.test-fixtures :refer [sample-router]]))

(deftest make-simple-path
  (is (= "/dashboard" (sut/make-path sample-router :route/dashboard))))

(deftest make-path-with-simple-param-test
  (is (= "/dashboard/users/1"
         (sut/make-path sample-router :route/user {:user-id 1}))))

(deftest makes-path-with-arbitrary-params
  (is (= "/dashboard/users/foobar"
         (sut/make-path sample-router :route/user {:user-id "foobar"})))
  (is (= "/dashboard/users/foobar"
         (sut/make-path sample-router :route/user {:user-id :foobar}))))

(deftest makes-path-nil-when-required-params-missing
  (is (= nil (sut/make-path sample-router :route/user))))
