(ns rhizome.mount-test
  (:require [cljs.test :refer [deftest is]]
            [rhizome.mount :as sut]
            [rhizome.test-utils :as tu]
            [rhizome.triggers :as triggers])
  (:require-macros [rhizome.macros :refer [with-html]]))

(def ^:private probe-attribute
  :rhizome.core/probe)

(defn- with-probe
  "Registers a trigger that only counts its runs, and hands `f` the counter."
  [f]
  (let [runs (atom [])]
    (triggers/register! {:attribute     probe-attribute
                         :default-event :event/input
                         :listener      (fn [_e directive _el]
                                          (swap! runs conj
                                            directive))})
    (try
      (f runs)
      (finally (swap! triggers/registry dissoc
                 probe-attribute)))))

(deftest runs-on-every-event-without-debounce-test
  (with-probe (fn [runs]
                (with-html [el [:input {probe-attribute "{:do :probe}"}]]
                           (sut/mount! el)
                           (tu/fire! el "input")
                           (tu/fire! el "input")
                           (is (= 2 (count @runs)))))))

(deftest debounce-waits-for-the-events-to-stop-test
  (let [clock (tu/fake-timers)]
    (try
      (with-probe (fn [runs]
                    (with-html
                      [el
                       [:input {probe-attribute "{:do :probe :debounce 300}"}]]
                      (sut/mount! el)
                      (tu/fire! el "input")
                      (.tick clock 200)
                      (tu/fire! el "input")
                      (.tick clock 200)
                      (is (= 0 (count @runs))
                          "a second event inside the window restarts the wait")
                      (.tick clock 100)
                      (is (= 1 (count @runs)) "and only the last event runs"))))
      (finally (.restore clock)))))

(deftest debounce-runs-again-after-its-window-test
  (let [clock (tu/fake-timers)]
    (try
      (with-probe (fn [runs]
                    (with-html [el
                                [:input {probe-attribute
                                         "{:do :probe :debounce 100}"}]]
                               (sut/mount! el)
                               (tu/fire! el "input")
                               (.tick clock 150)
                               (tu/fire! el "input")
                               (.tick clock 150)
                               (is (= 2 (count @runs))))))
      (finally (.restore clock)))))

(deftest debounce-reads-the-directive-at-event-time-test
  (let [clock (tu/fake-timers)]
    (try
      (with-probe (fn [runs]
                    (with-html [el
                                [:input {probe-attribute
                                         "{:do :first :debounce 100}"}]]
                               (sut/mount! el)
                               (.setAttribute el
                                              "rhizome_core___probe"
                                              "{:do :second :debounce 100}")
                               (tu/fire! el "input")
                               (.tick clock 150)
                               (is (= [{:do       :second
                                        :debounce 100}]
                                      @runs)))))
      (finally (.restore clock)))))
