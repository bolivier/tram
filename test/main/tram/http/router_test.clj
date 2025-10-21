(ns tram.http.router-test
  (:require [clojure.test :as t :refer [deftest is]]
            [clojure.zip :as zip]
            [matcher-combinators.test]
            [test-app.handlers.authentication-handlers :refer [routes] :as auth]
            [test-app.views.authentication-views :as views]
            [tram.http.router :as sut]
            [tram.rendering.template-renderer :refer [get-view-fn]]))

#_(def route-data
    (macroexpand
      '(tram.core/defroutes
        routes
        [""
         {:layout view/layout}
         ["/sign-in"
          {:get  sign-in
           :name :route/sign-in}]
         ["/forgot-password"
          {:get          :view/forgot-password
           :interceptors [{:enter identity
                           :name  :identity}]
           :name         :route/forgot-password
           :post         forgot}]
         ["/healthcheck"
          {:get  (fn [_] {:status 200})
           :name :route/healthcheck
           :post (constantly {:status 200})}]])))

#_(deftest route-data-macroexpansion-test
    (is
      (match?
        '(def
          routes
          [""
           {:interceptors [{:enter fn?
                            :name  :tram.http.interceptors/layout-interceptor}]
            :layout       view/layout
            :namespace    "tram.http.router-test"}
           ["/sign-in"
            {:get       tram.http.router-test/sign-in
             :name      :route/sign-in
             :namespace "tram.http.router-test"}]
           ["/forgot-password"
            {:get          {:handler     fn?
                            :handler-var nil}
             :interceptors [{:enter clojure.core/identity
                             :name  :identity}]
             :name         :route/forgot-password
             :namespace    "tram.http.router-test"
             :post         tram.http.router-test/forgot}]
           ["/healthcheck"
            {:get       {:handler (fn [_] {:status 200})}
             :name      :route/healthcheck
             :namespace "tram.http.router-test"
             :post      {:handler (clojure.core/constantly {:status 200})}}]])
        route-data)))

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
    (is (match? {:interceptors [{:name
                                 :tram.http.interceptors/layout-interceptor}]}
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
                   :post {:handler
                          (constantly {:status 200})}
                   :name :route/healthcheck}]
                healthcheck-route-data))))

(defn foo [])

(deftest coerce-route-entries-to-specs-test
  (is (match? {:interceptors [{:name  :tram.http.interceptors/layout-interceptor
                               :enter fn?}]
               :layout       views/layout}
              (sut/coerce-route-entries-to-specs {:layout views/layout}))))
