(ns rapid-test.hiccup-zipper-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.zip :as zip]
            [rapid-test.hiccup-zipper :as sut]))

(deftest hiccup-zipper-test
  (testing "Skips props"
    (is (= [:li 0]
           (zip/node (zip/next (sut/hiccup-zipper [:ul {:id :my-id}
                                                   [:li 0]
                                                   [:li 1]
                                                   [:li 2]]))))))
  (testing "List enclosed children from `for` loop"
    (is (= (zip/node (zip/next (sut/hiccup-zipper [:ul
                                                   (for [n (range 3)]
                                                     [:li (str n)])])))
           (zip/node (zip/next (sut/hiccup-zipper
                                 [:ul [:li "0"] [:li "1"] [:li "2"]]))))))
  (testing "getting children"
    (is (= "error" (zip/node (zip/next (sut/hiccup-zipper [:span "error"])))))))
