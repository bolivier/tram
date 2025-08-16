(ns tram.http.router-test
  (:require [clojure.zip :as zip]
            [expectations.clojure.test :as e]
            [test-app.handlers.authentication-handlers :refer [routes] :as auth]
            [test-app.views.authentication-views]
            [tram.http.router :as sut]))

(defn get-spec-from-routes [routes route-uri]
  (loop [routes (zip/vector-zip routes)]
    (cond
      (zip/end? routes) nil
      (= route-uri (zip/node routes)) (zip/node (zip/next routes))
      :else (recur (zip/next routes)))))

(e/defexpect defroutes
  (let [sign-in-route-data (get-spec-from-routes routes "/sign-in")]
    (e/expect {:name      :route/sign-in
               :get       {:handler     auth/sign-in
                           :handler-var #'auth/sign-in}
               :namespace "test-app.handlers.authentication-handlers"}
              (e/in sign-in-route-data))))

(e/defexpect expand-handler-entries
  ;; trivial case
  (e/expect [] (sut/map-routes identity []))
  (let [sign-in-route         (get-spec-from-routes routes "/sign-in")
        forgot-password-route (get-spec-from-routes routes "/forgot-password")
        healthcheck-route     (get-spec-from-routes routes "/healthcheck")]
    ;; sign-in route
    (e/expect fn? (get-in sign-in-route [:get :handler]))
    (e/expect :route/sign-in (get-in sign-in-route [:name]))
    (e/expect "test-app.handlers.authentication-handlers"
              (get-in sign-in-route [:namespace]))
    ;; forgot password route
    (let [{:keys [name namespace]} forgot-password-route
          get-spec  (:get forgot-password-route)
          post-spec (:post forgot-password-route)]
      (e/expect :route/forgot-password name)
      (e/expect "test-app.handlers.authentication-handlers" namespace)
      (e/expect (e/more-> fn? :handler) get-spec)
      (e/expect (e/more-> fn? :handler) post-spec))
    ;; healthcheck
    (let [{:keys [name namespace]} healthcheck-route
          get-spec  (:get forgot-password-route)
          post-spec (:post forgot-password-route)]
      (e/expect :route/healthcheck name)
      (e/expect "test-app.handlers.authentication-handlers" namespace)
      (e/expect (e/more-> fn? :handler) get-spec)
      (e/expect (e/more-> fn? :handler) post-spec))))

(defn foo [])

(e/defexpect ->handler-spec
  (let [spec (sut/->handler-spec #'foo)]
    (e/expect #'foo (:handler-var spec))
    (e/expect (= foo (:handler spec))))

  (let [spec (sut/->handler-spec (fn [req] {:status 200}))]
    (e/expect nil (:handler-var spec))
    (e/expect fn? (:handler spec))))
