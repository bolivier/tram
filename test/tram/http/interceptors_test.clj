(ns tram.http.interceptors-test
  (:require [clojure.test :as t]
            [matcher-combinators.test]
            [reitit.core :as r]
            [tram.http.interceptors :as sut]
            [tram.http.route-helpers :refer [make-route]]
            [tram.test-fixtures :refer [sample-router]]))

(defn component []
  [:a {:href :route/dashboard}])

(t/deftest expanding-hiccup
  (let [expander (:leave sut/expand-hiccup-interceptor)]
    (t/is (match? {:request  {::r/router sample-router}
                   :response {:headers {"hx-redirect" "/dashboard"}}}
                  (expander {:request  {::r/router sample-router}
                             :response {:headers {"hx-redirect"
                                                  :route/dashboard}
                                        :body    [component]}})))
    (t/is (match? {:response {:body [:a {:href "/dashboard"}]}}
                  (expander {:request  {::r/router sample-router}
                             :response {:body [component]}})))
    (t/is (match? {:response {:body [:a {:href "/dashboard"}]}}
                  (expander {:request  {::r/router sample-router}
                             :response {:body [:a {:href
                                                   (make-route
                                                     :route/dashboard)}]}})))))

(t/deftest layout-interceptor
  (let [layout-fn (fn [children] [:div#layout children])
        ctx       (apply (:enter (sut/layout-interceptor layout-fn)) [{}])]
    (t/is (match? {:layouts [layout-fn]} ctx)
          "Layout interceptor did not add layout-fn to :layouts key.")))
