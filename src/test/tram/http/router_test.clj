(ns tram.http.router-test
  (:require
    [expectations.clojure.test :as e]
    [sample-app.handlers.sign-in-handlers :refer [routes sign-in-handler]]
    [sample-app.views.sign-in-views]
    [tram.http.router]
    [tram.test-fixtures :refer [ok-good-handler]]))

(e/defexpect defroutes
  (let [sign-in-route*   (second routes)
        dashboard-route* (nth routes 2)
        with-ns-route*   (nth routes 3)]
    (e/expect ["/with-ns"
               {:name      :route/with-ns
                :namespace "foobar"}]
              with-ns-route*)
    (e/expect ["/sign-in"
               {:name      :route/sign-in
                :get       {:handler     sign-in-handler
                            :handler-var #'sign-in-handler}
                :namespace "sample-app.handlers.sign-in-handlers"}]
              sign-in-route*)
    ;; These are kind of hard to read because of how these tests work
    (let [[_ dashboard-fragment [_ user-fragment]] dashboard-route*]
      (e/expect [""
                 {:name      :route/dashboard
                  :namespace "sample-app.handlers.sign-in-handlers"}]
                dashboard-fragment)
      (e/expect {:namespace "sample-app.handlers.sign-in-handlers"
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
      (when (e/expect fn?
                      (:handler (:get user-fragment)))
        (e/expect {:status 200
                   :body   "show user page"}
                  (apply (:handler (:get user-fragment))
                    [{}]))))))
