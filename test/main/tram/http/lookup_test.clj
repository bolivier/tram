(ns tram.http.lookup-test
  (:require [clojure.test :refer [deftest is]]
            [rapid-test.core :as rt]
            [test-app.handlers.authentication-handlers :refer [test-router]]
            [test-app.views.authentication-views]
            [tram.http.lookup :as sut]
            [tram.test-fixtures :refer [tram-config]]))

(deftest getting-default-template
  (rt/with-stub [_ {:fn      tram.core/get-tram-config
                    :returns tram-config}]
    (is (match? #'test-app.views.authentication-views/sign-in
                (sut/request->template {:uri "/sign-in"
                                        :request-method :get
                                        :reitit.core/router test-router})))))
