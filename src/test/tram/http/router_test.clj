(ns tram.http.router-test
  (:require
    [expectations.clojure.test :as e]
    [test-app.handlers.authentication-handlers :refer [routes sign-in] :as auth]
    [test-app.views.authentication-views]
    [tram.http.router]
    [tram.test-fixtures :refer [ok-good-handler]]))

(e/defexpect defroutes
  (let [sign-in-route-data (-> auth/routes
                               first
                               second)]
    (e/expect {:name      :route/sign-in
               :get       {:handler     auth/sign-in
                           :handler-var #'auth/sign-in}
               :namespace "test-app.handlers.authentication-handlers"}
              (e/in sign-in-route-data)))
  #_(e/expect ["/with-ns"
               {:name      :route/with-ns
                :namespace "foobar"}]
              with-ns-route*)

  ;; These are kind of hard to read because of how these tests work

  #_(let [[_ dashboard-fragment [_ user-fragment]] dashboard-route*]
      (e/expect [""
                 {:name      :route/dashboard
                  :namespace "test-app.handlers.authentication-handlers"}]
                dashboard-fragment)
      (e/expect {:namespace "test-app.handlers.authentication-handlers"
                 :name      :route/user
                 :patch     {:handler     ok-good-handler
                             :handler-var #'ok-good-handler}
                 :put       {:handler     ok-good-handler
                             :handler-var #'ok-good-handler}
                 :delete    {:handler     ok-good-handler
                             :handler-var #'ok-good-handler}
                 :post      {:handler     ok-good-handler
                             :handler-var #'ok-good-handler}}
                (e/in user-fragment))
      (e/expecting "default view renders the value in the template key")
      (e/expect {:status   200
                 :template :views/show-user}
                (e/in ((:handler (:get user-fragment)) nil)))))
