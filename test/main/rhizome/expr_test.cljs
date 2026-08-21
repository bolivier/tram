(ns rhizome.expr-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [rhizome.core :as core]
            [rhizome.expr :as sut]
            [rhizome.mount :as mount]
            [rhizome.signals :as signals]
            [rhizome.test-utils :as tu])
  (:require-macros [rhizome.macros :refer [with-html]]))

(use-fixtures :each
              {:before (fn []
                         (reset! signals/registry {}))})

(deftest dependencies-read-off-the-form-statically-test
  (is (= #{:a :b}
         (sut/dependencies '(and (signal :a) (not (signal :b)) "literal")))))

(deftest a-keyword-inside-a-form-is-a-literal-test
  (is (= #{:status}
         (sut/dependencies '(= (signal :status) :status/active)))))

(deftest signal-takes-one-literal-keyword-test
  (is (thrown? js/Error (sut/dependencies '(not (signal (signal :a))))))
  (is (thrown? js/Error (sut/dependencies '(not (signal :a :b))))))

(deftest evaluate-reads-signals-and-calls-the-allowlist-test
  (signals/put! :filename "book.edn")
  (is (= "picked book.edn"
         (sut/evaluate '(str "picked " (signal :filename))))))

(deftest and-and-or-short-circuit-test
  (testing "the unlisted function after the deciding value never runs"
    (is (false? (sut/evaluate '(and false (explode)))))
    (is (= 1 (sut/evaluate '(or 1 (explode)))))))

(deftest a-head-outside-the-allowlist-throws-test
  (is (thrown? js/Error (sut/evaluate '(alert "hi")))))

(deftest watch-applies-now-and-on-every-change-test
  (let [seen (atom [])]
    (signals/put! :filename nil)
    (sut/watch! '(not (signal :filename))
                #(swap! seen conj %))
    (signals/put! :filename "book.edn")
    (is (= [true false] @seen))))

(deftest a-top-level-keyword-is-sugar-for-its-signal-test
  (let [seen (atom [])]
    (signals/put! :greeting "hello")
    (sut/watch! :greeting
                #(swap! seen conj %))
    (is (= ["hello"] @seen))))

(deftest a-constant-form-applies-once-test
  (let [seen (atom [])]
    (sut/watch! '(not true)
                #(swap! seen conj %))
    (is (= [false] @seen))))

(deftest a-form-follows-every-signal-it-reads-test
  (let [seen (atom [])]
    (sut/watch! '(and (signal :a) (signal :b))
                #(swap! seen conj %))
    (signals/put! :a true)
    (signals/put! :b "ok")
    (is (= "ok" (last @seen)))))

(deftest show-follows-a-negated-expression-test
  (core/init)
  (with-html [el
              [:div
               [:span {:rhizome.core/show "(not (signal :filename))"}
                "drop a file"]
               [:input {:rhizome.core/bind "[:filename nil]"}]]]
             (mount/mount! el)
             (let [span  (.querySelector el "span")
                   input (.querySelector el "input")]
               (is (= "" (.. span -style -display))
                   "no filename yet, so the prompt shows")
               (tu/type! input "book.edn")
               (is (= "none" (.. span -style -display))))))
