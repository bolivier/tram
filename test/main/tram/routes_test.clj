(ns tram.routes-test
  (:require [clojure.test :refer [deftest is]]
            [malli.core :as m]
            [malli.transform :as mt]
            [matcher-combinators.test]
            [reitit.coercion :as coercion]
            [reitit.core :as r]
            [reitit.http :as http]
            [reitit.ring :as ring]
            test-app.handlers.authentication-handlers
            [tram.executor :as executor]
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
                 {:get {:template
                        (list
                          'quote
                          'test-app.views.authentication-views/forgot-password)}
                  :name keyword?}]
                route-data))))

(deftest string-to-vector-coercion
  (let [schema    [:map [:tags [:vector :string]]]
        transform (mt/transformer (sut/string->vector-transformer)
                                  (mt/string-transformer))]
    (is (= {:tags ["foo"]} (m/decode schema {:tags "foo"} transform)))
    (is (= {:tags ["foo" "bar"]}
           (m/decode schema {:tags ["foo" "bar"]} transform)))))

(deftest default-values-on-query-params
  (let [schema  [:map
                 [:page {:default 1}
                  :int]
                 [:q :string]]
        coercer (coercion/-request-coercer sut/coercion :string schema)]
    ;; missing :page should get default value of 1
    (is (= {:page 1
            :q    "hello"}
           (coercer {:q "hello"} nil)))
    ;; provided :page should be coerced from string
    (is (= {:page 2
            :q    "hello"}
           (coercer {:page "2"
                     :q    "hello"}
                    nil)))))

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

(defn- view-route [path template]
  [path {:get {:template       template
               :view-required? true}}])

(deftest assert-views-exist-passes-when-the-view-exists
  (is (nil? (sut/assert-views-exist!
              [(view-route "/a"
                           'test-app.views.authentication-views/sign-in)]))))

(deftest assert-views-exist-reports-every-missing-view-at-once
  (let [e (try
            (sut/assert-views-exist!
              [(view-route "/a" 'test-app.views.authentication-views/nope)
               (view-route "/b"
                           'test-app.views.authentication-views/also-nope)])
            (catch clojure.lang.ExceptionInfo e
              e))]
    (is (match? {:error   :missing-views
                 :missing [{:path "/a"} {:path "/b"}]}
                (ex-data e)))
    (is (re-find #"/a.*has no view" (ex-message e)))
    (is (re-find #"/b.*has no view" (ex-message e)))))

(deftest assert-views-exist-ignores-routes-that-do-not-promise-a-view
  (is (nil? (sut/assert-views-exist!
              [["/a"
                {:get {:template
                       'test-app.views.authentication-views/nope}}]]))))

(deftest tram-router-accepts-handler-routes-without-views
  (is (some? (sut/tram-router
               test-app.handlers.authentication-handlers/routes))))

(deftest flatten-interceptors-splices-groups
  (is (= [{:name :a} {:name :b} {:name :c} {:name :d}]
         (sut/flatten-interceptors
           [{:name :a} [{:name :b} {:name :c}] {:name :d}]))))

(deftest assert-interceptor-order-passes-when-satisfied
  (is (nil? (sut/assert-interceptor-order! [{:name :tram/format}
                                            {:name :tram/csrf
                                             :must-run-after
                                             [:tram/format]}]))))

(deftest assert-interceptor-order-throws-on-missing-dependency
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"must run after :tram/format, but :tram/format is not in the chain"
        (sut/assert-interceptor-order! [{:name :tram/csrf
                                         :must-run-after [:tram/format]}]))))

(deftest assert-interceptor-order-throws-when-dependency-runs-later
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"must run after :tram/format, but :tram/format appears later"
        (sut/assert-interceptor-order! [{:name :tram/csrf
                                         :must-run-after [:tram/format]}
                                        {:name :tram/format}]))))

(deftest wire-format-group-coerces-through-renamed-chain
  (let [captured (atom :unset)
        routes   [["/echo"
                   {:name :route/echo
                    :get  {:handler    (fn [req]
                                         (reset! captured (get-in req
                                                                  [:parameters
                                                                   :query]))
                                         {:status 200
                                          :body   "ok"})
                           :parameters {:query [:map [:page :int]]}}}]]
        router   (sut/tram-router routes
                                  {:data {:coercion     sut/coercion
                                          :interceptors (sut/wire-format)}})
        app      (http/ring-handler router
                                    (ring/create-default-handler)
                                    {:executor executor/executor})]
    (try
      (app {:request-method :get
            :uri            "/echo"
            :query-string   "page=2"
            :headers        {"accept" "text/html"}})
      (catch Throwable _
        nil))
    (is (= {:page 2} @captured))))
