(ns tram.http.router-test
  (:require [clojure.test :as t :refer [deftest is]]
            [clojure.zip :as zip]
            [test-app.handlers.authentication-handlers :refer [routes] :as auth]
            [test-app.views.authentication-views :as views]
            [tram.http.router :as sut]))

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

(deftest defroutes
  (let [sign-in-route-data (get-spec-from-routes routes "/sign-in")]
    (is (match? {:name      :route/sign-in
                 :get       {:handler     auth/sign-in
                             :handler-var #'auth/sign-in}
                 :namespace "test-app.handlers.authentication-handlers"}
                sign-in-route-data)))
  (let [global-route-data (get-spec-from-routes routes "")]
    (is (match? {:interceptors
                 [{:name  :tram.http.interceptors/layout-interceptor
                   :enter fn?}]}
                global-route-data)
        "Router did not add layout-interceptor to :interceptors key.")
    (let [enter-fn (:enter (first (:interceptors global-route-data)))
          context-after-interceptor (enter-fn {})]
      (is (match? {:layouts [#'test-app.views.authentication-views/layout]}
                  context-after-interceptor)
          "Calling layout interceptor did not add to layouts key in ctx."))))

(deftest expand-handler-entries
  ;; trivial case
  (is (= [] (sut/map-routes identity [])))
  (let [sign-in-route         (get-spec-from-routes routes "/sign-in")
        forgot-password-route (get-spec-from-routes routes "/forgot-password")
        healthcheck-route     (get-spec-from-routes routes "/healthcheck")]
    ;; sign-in route
    (is (match? {:get       {:handler     fn?
                             :handler-var var?}
                 :name      :route/sign-in
                 :namespace "test-app.handlers.authentication-handlers"}
                sign-in-route))
    ;; forgot password route
    (is (match? {:get          {:handler fn?}
                 :post         {:handler fn?}
                 :name         :route/forgot-password
                 :interceptors [{:name  :identity
                                 :enter identity}]
                 :namespace    "test-app.handlers.authentication-handlers"}
                forgot-password-route))
    ;; healthcheck
    (is (match? {:get       {:handler fn?}
                 :post      {:handler fn?}
                 :name      :route/healthcheck
                 :namespace "test-app.handlers.authentication-handlers"}
                healthcheck-route))))

(defn foo [])

(deftest ->handler-spec
  (let [spec (sut/->handler-spec #'foo)]
    (is (match? #'foo (:handler-var spec)))
    (is (= foo (:handler spec))))
  (let [spec (sut/->handler-spec (fn [req] {:status 200}))]
    (is (nil? (:handler-var spec)))
    (is (fn? (:handler spec)))))

(deftest coerce-route-entries-to-specs-test
  (is (match? {:interceptors [{:name  :tram.http.interceptors/layout-interceptor
                               :enter fn?}]
               :layout       views/layout}
              (sut/coerce-route-entries-to-specs {:layout views/layout}))))
