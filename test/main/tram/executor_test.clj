(ns tram.executor-test
  (:require [clojure.test :refer [deftest is testing]]
            [reitit.interceptor :as interceptor]
            [tram.executor :as sut]
            [tram.vars :refer [*current-user* *req* *res*]]))

(defn- recording-interceptor
  "An interceptor that appends `[name stage]` to the `log` atom at every stage."
  [log name]
  {:name  name
   :enter (fn [ctx]
            (swap! log conj
              [name :enter])
            ctx)
   :leave (fn [ctx]
            (swap! log conj
              [name :leave])
            ctx)
   :error (fn [ctx]
            (swap! log conj
              [name :error])
            ctx)})

(defn- responder
  "An interceptor whose `:enter` sets `response`."
  [log name response]
  (assoc (recording-interceptor log name)
    :enter (fn [ctx]
             (swap! log conj
               [name :enter])
             (assoc ctx :response response))))

(deftest enters-run-forward-and-leaves-run-backward
  (let [log (atom [])]
    (sut/execute [(recording-interceptor log :a)
                  (recording-interceptor log :b)
                  (fn [_] {:status 200})]
                 {})
    (is (= [[:a :enter] [:b :enter] [:b :leave] [:a :leave]] @log))))

(deftest req-is-bound-at-every-stage
  (let [seen (atom [])
        note (fn [stage]
               (fn [ctx]
                 (swap! seen conj
                   [stage *req*])
                 ctx))]
    (sut/execute [{:enter (note :outer-enter)
                   :leave (note :outer-leave)
                   :error (note :outer-error)}
                  {:enter (fn [ctx]
                            (swap! seen conj
                              [:rebind-enter *req*])
                            (assoc-in ctx [:request :changed?] true))}
                  {:enter (note :inner-enter)
                   :error (note :inner-error)}
                  {:enter (fn [_] (throw (ex-info "boom" {})))}]
                 {:uri "/"}
                 identity
                 identity)
    (is (= [[:outer-enter {:uri "/"}]
            [:rebind-enter {:uri "/"}]
            [:inner-enter {:uri      "/"
                           :changed? true}]
            [:inner-error {:uri      "/"
                           :changed? true}]
            [:outer-error {:uri      "/"
                           :changed? true}]]
           @seen))
    (is (nil? *req*))))

(deftest current-user-and-res-follow-the-context
  (let [seen (atom [])
        note (fn [stage]
               (fn [ctx]
                 (swap! seen conj
                   [stage *current-user* *res*])
                 ctx))]
    (sut/execute [{:enter (note :outer-enter)
                   :leave (note :outer-leave)}
                  {:enter (fn [ctx]
                            (assoc-in ctx [:request :current-user] :alice))}
                  {:enter (note :inner-enter)
                   :leave (note :inner-leave)}
                  (fn [_] {:status 200})]
                 {})
    (is (= [[:outer-enter nil nil]
            [:inner-enter :alice nil]
            [:inner-leave :alice {:status 200}]
            [:outer-leave :alice {:status 200}]]
           @seen))
    (is (nil? *current-user*))
    (is (nil? *res*))))

(deftest non-nil-response-after-enter-short-circuits-the-chain
  (let [log      (atom [])
        response (sut/execute [(recording-interceptor log :a)
                               (responder log :b {:status 204})
                               (recording-interceptor log :c)
                               (fn [_] {:status 200})]
                              {})]
    (is (= {:status 204} response))
    (is (= [[:a :enter] [:b :enter] [:b :leave] [:a :leave]] @log))))

(deftest leave-can-still-change-a-short-circuited-response
  (let [response (sut/execute
                   [{:leave (fn [ctx]
                              (assoc-in ctx [:response :headers "x"] "y"))}
                    {:enter (fn [ctx] (assoc ctx :response {:status 204}))}
                    (fn [_] {:status 200})]
                   {})]
    (is (= {:status  204
            :headers {"x" "y"}}
           response))))

(deftest an-error-skips-remaining-enters-and-runs-error-stages
  (let [log      (atom [])
        recover  {:name  :recover
                  :error (fn [ctx]
                           (swap! log conj
                             [:recover :error])
                           (-> ctx
                               (dissoc :error)
                               (assoc :response {:status 500})))}
        response (sut/execute [(recording-interceptor log :a)
                               recover
                               (recording-interceptor log :b)
                               (fn [_] (throw (ex-info "boom" {})))]
                              {})]
    (is (= {:status 500} response))
    (is (= [[:a :enter] [:b :enter] [:b :error] [:recover :error] [:a :leave]]
           @log))))

(deftest unhandled-errors-throw-or-raise
  (let [chain [(fn [_] (throw (ex-info "boom" {:from :handler})))]]
    (testing "two-arity"
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"boom"
                            (sut/execute chain {}))))
    (testing "four-arity"
      (let [raised (atom nil)]
        (is (nil? (sut/execute chain
                               {}
                               (fn [_]
                                 (reset! raised :responded))
                               (fn [e]
                                 (reset! raised (ex-data e))))))
        (is (= {:from :handler} @raised))))))

(deftest a-handler-returning-an-exception-becomes-an-error
  (let [raised (atom nil)]
    (sut/execute [(fn [_] (ex-info "returned" {}))]
                 {}
                 (fn [_]
                   (reset! raised :responded))
                 (fn [e]
                   (reset! raised (ex-message e))))
    (is (= "returned" @raised))))

(deftest reitit-handler-interceptors-run-as-handlers
  (let [handler     (fn [req]
                      {:status 200
                       :body   (:uri req)})
        interceptor (interceptor/into-interceptor handler nil {})
        queue       (interceptor/queue sut/executor [interceptor])]
    (is (instance? clojure.lang.PersistentQueue queue))
    (is (= {:status 200
            :body   "/x"}
           (interceptor/execute sut/executor queue {:uri "/x"})))
    (is (= {:status 200
            :body   "/x"}
           (interceptor/execute sut/executor [interceptor] {:uri "/x"})))))

(deftest an-empty-chain-yields-nil
  (is (nil? (sut/execute [] {})))
  (is (nil? (sut/execute nil {})))
  (let [result (atom nil)]
    (sut/execute nil
                 {}
                 (fn [r]
                   (reset! result [:respond r]))
                 identity)
    (is (= [:respond nil] @result))))

(deftest a-stage-returning-a-non-context-becomes-an-error
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Unsupported Context on :enter"
                        (sut/execute [{:enter (fn [_] nil)}] {}))))

(deftest run-leaves-runs-only-the-response-side
  (let [log (atom [])
        ctx (sut/run-leaves {:request  {:uri "/"}
                             :response {:status 200}}
                            [(recording-interceptor log :a)
                             (recording-interceptor log :b)
                             {:name  :seen-req
                              :leave (fn [ctx]
                                       (swap! log conj
                                         [:seen-req *req*])
                                       ctx)}
                             (fn [_] {:status 500})])]
    (is (= {:status 200} (:response ctx)))
    (is (= [[:seen-req {:uri "/"}] [:b :leave] [:a :leave]]
           @log)))
  (testing "a failing leave sets :error"
    (let [ctx (sut/run-leaves {:response {:status 200}}
                              [{:leave (fn [_] (throw (ex-info "boom" {})))}])]
      (is (= "boom" (ex-message (:error ctx)))))))