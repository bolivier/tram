(ns tram.http.lookup-test
  (:require [expectations.clojure.test :as e]
            [sample-app.handlers.sign-in-handlers :refer [router]]
            [sample-app.views.sign-in-views]
            [tram.http.lookup :as sut]
            [tram.test-fixtures :refer [with-tram-config]]))

(e/defexpect getting-default-template
  (e/expect #'sample-app.views.sign-in-views/sign-in
            (with-tram-config
              (sut/request->template {:uri "/sign-in"
                                      :request-method :get
                                      :reitit.core/router router}))))
