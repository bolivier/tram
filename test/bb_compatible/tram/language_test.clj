(ns tram.language-test
  (:require [clojure.test :refer [deftest is]]
            [test-app.handlers.authentication-handlers]
            [tram.language :as sut]))

(def views-ns
  (the-ns 'test-app.views.authentication-views))
(def handlers-ns
  (the-ns 'test-app.handlers.authentication-handlers))

(deftest convert-handler-ns-test
  (is (= (str views-ns) (sut/convert-ns handlers-ns :view)))
  (is (= (str handlers-ns) (sut/convert-ns handlers-ns :handler))))

(deftest convert-view-ns-test
  (is (= (str views-ns) (sut/convert-ns views-ns :view)))
  (is (= (str handlers-ns) (sut/convert-ns views-ns :handler))))

(deftest modelize-singular
  (let [tests [[:models/users :users]
               [:models/users :models/users]
               [:models/settings :settings]]]
    (doseq [[expected input] tests]
      (is (= expected (sut/modelize input {:plural? false}))))))

(deftest modelize-plural
  (let [tests [[:models/users :user]
               [:models/users :models/user]
               [:models/settings :setting]]]
    (doseq [[expected input] tests]
      (is (= expected (sut/modelize input))))))
