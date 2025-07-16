(ns tram.http.lookup-test
  (:require [expectations.clojure.test :as e]
            [test-app.handlers.authentication-handlers :refer [test-router]]
            [test-app.views.authentication-views]
            [tram.http.lookup :as sut]
            [tram.test-fixtures :refer [with-tram-config]]))

(e/defexpect getting-default-template
  (e/expect #'test-app.views.authentication-views/sign-in
            (with-tram-config
              (sut/request->template {:uri "/sign-in"
                                      :request-method :get
                                      :reitit.core/router test-router}))))
