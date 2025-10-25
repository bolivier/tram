(ns tram.routes-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.zip :as zip]
            [matcher-combinators.test]
            [reitit.core :as r]
            [test-app.handlers.authentication-handlers :refer [routes] :as auth]
            [test-app.views.authentication-views :as views]
            [tram.routes :as sut]
            [tram.test-fixtures :refer [sample-router]]))

(defn component []
  [:a {:href :route/dashboard}])

(deftest expanding-hiccup
  (let [expander (:leave sut/expand-hiccup-interceptor)]
    (is (match? {:request  {::r/router sample-router}
                 :response {:headers {"hx-redirect" "/dashboard"}}}
                (expander {:request  {::r/router sample-router}
                           :response {:headers {"hx-redirect" :route/dashboard}
                                      :body    [component]}})))
    (is (match? {:response {:body [:a {:href "/dashboard"}]}}
                (expander {:request  {::r/router sample-router}
                           :response {:body [component]}})))
    (is (match? {:response {:body [:a {:href "/dashboard"}]}}
                (expander {:request  {::r/router sample-router}
                           :response {:body [:a {:href
                                                 (sut/make-route
                                                   :route/dashboard)}]}})))))

(defn get-spec-from-routes
  "Takes the routes to search, and a route uri to search for. Finds the uri and
  returns the handler spec, which is the next value.

  Note, the route uri is a naive string comparison, so it will not work on nested/split routes"
  [routes route-uri]
  (loop [routes (zip/vector-zip routes)]
    (cond
      (zip/end? routes) nil
      (= route-uri (zip/node routes)) (zip/node (zip/next routes))
      :else (recur (zip/next routes)))))

(deftest layout-interceptor-added-from-key
  (let [global-route-data (get-spec-from-routes routes "")]
    (is (match? {:interceptors [{:name :tram.impl.router/layout-interceptor}]}
                global-route-data)
        "Router did not add layout-interceptor to :interceptors key.")
    (let [enter-fn (:enter (first (:interceptors global-route-data)))
          context-after-interceptor (enter-fn {})]
      (is (match? {:layouts [#'test-app.views.authentication-views/layout]}
                  context-after-interceptor)
          "Calling layout interceptor did not add to layouts key in ctx."))))

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
