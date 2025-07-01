(ns tram.http.routing-test
  (:require [expectations.clojure.test :as e]
            [tram.test-fixtures :refer [sample-router]]
            [tram.http.routing :as sut]))

(e/defexpect make-path
  (e/expect "/dashboard" (sut/make-path sample-router :route/dashboard))
  (e/expect "/dashboard/users/1"
            (sut/make-path sample-router :route/user {:user-id 1}))
  (e/expect nil (sut/make-path sample-router :route/user))
  (e/expect "/dashboard/users/foobar"
            (sut/make-path sample-router :route/user {:user-id "foobar"}))
  (e/expect "/dashboard/users/foobar"
            (sut/make-path sample-router :route/user {:user-id :foobar})))
