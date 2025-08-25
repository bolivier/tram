(ns tram.http.interceptors-test
  (:require [clojure.test :as t]
            [reitit.core :as r]
            [tram.http.interceptors :as sut]
            [tram.test-fixtures :refer [sample-router]]))

(t/deftest expanding-hiccup
  (let [expander (:leave sut/expand-hiccup-interceptor)]
    (t/is (match? {:response {:body [:a {:href "/dashboard"}]}}
                  (expander {:request  {::r/router sample-router}
                             :response {:body [:a {:href
                                                   :route/dashboard}]}})))))

(t/deftest layout-interceptor
  (let [layout-fn (fn [children] [:div#layout children])
        ctx       (apply (:enter (sut/layout-interceptor layout-fn)) [{}])]
    (t/is (match? {:layouts [layout-fn]} ctx)
          "Layout interceptor did not add layout-fn to :layouts key.")))
