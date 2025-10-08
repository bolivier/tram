(ns tram.testing.mocks-test
  (:require [tram.testing.mocks :as sut]
            [clojure.test :as t :refer [deftest is testing]]
            [matcher-combinators.test]
            [matcher-combinators.matchers :as m]))

(def called-for-real? (atom false))
(defn fake-fn [& _]
  (throw (ex-info "Should not be called" {:reason :called-fake-fn})))

(defn another-fake-fn [& _]
  (throw (ex-info "Should not be called" {:reason :called-another-fake-fn})))

(deftest with-stub
  (testing "simple skips the fn"
    (sut/with-stub [calls fake-fn]
      (fake-fn)

      (is (match? false @called-for-real?) "fake-fn should not be called")
      (is (match? [{:args nil
                    :func fn?}]
                  @calls) "calls were incorrect.")))

  (testing "calls are tracked"
    (sut/with-stub [calls fake-fn]
      (fake-fn)
      (fake-fn 123)
      (fake-fn [123] :foobar :test)

      (is (match? false @called-for-real?) "fake-fn should not be called")

      (is (match? [{:args nil}
                   {:args [123]}
                   {:args [[123] :foobar :test]}]
                  @calls) "calls were incorrect.")))

  (testing "With return behavior"
    (sut/with-stub [_ {:fn fake-fn
                       :returns 42}]
      (is (= 42 (fake-fn)))
      (is (= 42 (fake-fn 123)))
      (is (= 42 (fake-fn :foobar 123)))

      (is (match? false @called-for-real?) "fake-fn should not be called")))

  (testing "With dynamic return behavior"
    (sut/with-stub [_ {:fn fake-fn
                       :impl (fn [n]
                               (inc n))}]
      (is (= 3 (fake-fn 2)))
      (is (= 5 (fake-fn 4)))

      (is (thrown-match? Exception
                         nil
                         (fake-fn nil)))

      (is (match? false @called-for-real?) "fake-fn should not be called")))

  (testing "multiple stubs in one"
    (sut/with-stub [calls-1 fake-fn
                    calls-2 another-fake-fn]
      (fake-fn)
      (another-fake-fn 2)
      (fake-fn 123)
      (another-fake-fn "test" :keyword)
      (fake-fn [123] :foobar :test)

      (is (match? false @called-for-real?) "fake-fn should not be called")
      (is (match? [{:args nil}
                   {:args [123]}
                   {:args [[123] :foobar :test]}]
                  @calls-1) "fake-fn calls were incorrect.")
      (is (match? [{:args [2]}
                   {:args ["test" :keyword]}]
                  @calls-2) "another-fake-fn calls were incorrect."))))
