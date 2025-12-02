(ns tram.routes-test
  (:require [clojure.test :refer [deftest is]]
            [matcher-combinators.test]
            [reitit.core :as r]
            test-app.handlers.authentication-handlers
            [tram.routes :as sut]
            [tram.test-fixtures :refer [sample-router]]))

(defn component []
  [:a {:href :route/dashboard}])

(deftest expanding-hiccup
  (let [expander (:leave sut/expand-header-routes-interceptor)
        expanded (expander {:request  {::r/router sample-router}
                            :response {:headers {"hx-redirect" :route/dashboard}
                                       :body    [component]}})]
    (is (match? "/dashboard"
                (get-in expanded [:response :headers "hx-redirect"])))
    (is (match? [fn?] ;; body not expanded
                (get-in expanded [:response :body])))))

(deftest forgot-password-adding-template-to-root
  (let
    [forgot-password-route-data
     (binding [*ns* (the-ns 'test-app.handlers.authentication-handlers)]
       (macroexpand '(tram.core/defroutes
                      routes
                      ["/forgot-password"
                       {:get  :view/forgot-password
                        :name :route/forgot-password}])))

     [_ _ route-data] forgot-password-route-data]
    (is (match? ["/forgot-password"
                 {:get  {:template
                         'test-app.views.authentication-views/forgot-password}
                  :name keyword?}]
                route-data))))

(deftest expand-healthcheck-entries
  (let
    [[_ _ healthcheck-route-data]
     (binding [*ns* (the-ns 'test-app.handlers.authentication-handlers)]
       (macroexpand '(tram.core/defroutes
                      routes
                      ["/healthcheck"
                       {:get  (fn [_] {:status 200})
                        :name :route/healthcheck
                        :post (constantly {:status 200})}])))]
    (is (match? '["/healthcheck"
                  {:get  {:handler (fn [_] {:status 200})}
                   :post {:handler (constantly {:status 200})}
                   :name :route/healthcheck}]
                healthcheck-route-data))))
