(ns tram.http.interceptors-test
  (:require [expectations.clojure.test :as e]
            [reitit.core :as r]
            [tram.http.interceptors :as sut]
            [tram.test-fixtures :refer [sample-router]]))

(def expander
  (:leave sut/expand-hiccup-interceptor))

(e/defexpect
  expanding-hiccup
  (e/expect [:a {:href "/dashboard"}]
            (-> (expander {:request  {::r/router sample-router}
                           :response {:body [:a {:href :route/dashboard}]}})
                :response
                :body)))

(e/defexpect layout-interceptor
  (e/expect 1 1))
