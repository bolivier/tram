(ns rhizome.signals-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [rhizome.core :as core]
            [rhizome.mount :as mount]
            [rhizome.signals :as sut]
            [rhizome.test-utils :as tu])
  (:require-macros [rhizome.macros :refer [with-html]]))

(use-fixtures :each
              {:before (fn []
                         (reset! sut/registry {}))})

(deftest a-listener-hears-the-current-value-on-arrival-test
  (let [seen (atom [])]
    (sut/put! :greeting "hello")
    (sut/listen! :greeting
                 #(swap! seen conj
                    %))
    (is (= ["hello"] @seen))))

(deftest a-listener-that-arrives-first-still-hears-the-value-test
  (testing "mount order is registry order, so a binding can beat its input"
    (let [seen (atom [])]
      (sut/listen! :greeting
                   #(swap! seen conj
                      %))
      (sut/put! :greeting "hello")
      (is (= [nil "hello"] @seen)))))

(deftest text-follows-the-input-bound-to-it-test
  (core/init)
  (with-html [el
              [:div
               [:span {:rhizome.core/text ":example-text"}]
               [:input {:rhizome.core/bind "[:example-text \"start\"]"}]]]
             (mount/mount! el)
             (let [span  (.querySelector el "span")
                   input (.querySelector el "input")]
               (is (= "start" (.-textContent span))
                   "the input seeds the signal on mount")
               (is (= "start" (.-value input)))
               (tu/type! input "-more")
               (is (= "start-more" (.-textContent span))))))

(deftest a-file-input-feeds-its-filename-through-bind-test
  (core/init)
  (with-html [el
              [:div
               [:span {:rhizome.core/text ":upload-name"}]
               [:input {:rhizome.core/bind "[:upload-name]"
                        :type "file"}]]]
             (mount/mount! el)
             (let [span  (.querySelector el "span")
                   input (.querySelector el "input")]
               (tu/attach-files! input [(tu/fake-file "book-inventory.edn")])
               (tu/fire! input "input")
               (is (= "book-inventory.edn" (.-textContent span))
                   "the echo write-back must not throw on a file input"))))

(deftest show-hides-while-its-checkbox-is-unchecked-test
  (core/init)
  (with-html [el
              [:div
               [:span {:rhizome.core/show ":visible?"}
                "content"]
               [:input {:rhizome.core/bind "[:visible? true]"
                        :type "checkbox"}]]]
             (mount/mount! el)
             (let [span     (.querySelector el "span")
                   checkbox (.querySelector el "input")]
               (is (= "" (.. span -style -display))
                   "a true signal leaves display to the stylesheet")
               (is (true? (.-checked checkbox))
                   "a checkbox binds through checked, not value")
               (set! (.-checked checkbox) false)
               (tu/fire! checkbox "input")
               (is (= "none" (.. span -style -display))))))
