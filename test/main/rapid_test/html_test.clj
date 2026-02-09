(ns rapid-test.html-test
  (:require [clojure.test :refer [deftest is testing]]
            [rapid-test.html :as sut]))

(deftest get-base-tag-test
  (is (= :div (sut/get-base-tag [:div.class])))
  (is (= :div (sut/get-base-tag [:div#my-id.class])))
  (is (= :div (sut/get-base-tag [:div#my-id]))))

(deftest get-props-test
  (is (= {:id :my-id} (sut/get-props [:input#my-id])))
  (is (= {:id   :my-id
          :name :email}
         (sut/get-props [:input#my-id {:name :email}])))
  (is (= {:id    :my-id
          :name  :email
          :class "my-class"}
         (sut/get-props [:input#my-id.my-class {:name :email}])))
  (is (= {:id    :my-id
          :name  :email
          :class "my-class my-other-class"}
         (sut/get-props [:input#my-id.my-class.my-other-class {:name :email}])))
  (is (= {:class "my-other-class my-class"}
         (sut/get-props [:input.my-class {:class "my-other-class"}])))
  (is (= {:name  :email
          :class "my-other-class"}
         (sut/get-props [:inputmy-class.my-other-class {:name :email}])))
  (is (= {:name  :email
          :class "my-class"}
         (sut/get-props [:input.my-class {:name :email}])))
  (is (= {:class "my-class"} (sut/get-props [:input.my-class]))))

(deftest get-attribute-test
  (is (= :email
         (sut/get-attribute [:input {:name :email}]
                            :name)))
  (is (= :my-id (sut/get-attribute [:input#my-id] :id)))
  (is (= "my-class" (sut/get-attribute [:input.my-class] :class))))

(deftest get-by-role-test
  (testing "list role"
    (is (= [:ul#error-list [:li "Something went wrong!"]]
           (sut/get-by-role :list
                            [:div
                             [:span "errors: "]
                             [:ul#error-list [:li "Something went wrong!"]]]))))
  (testing "list item role"
    (is (= nil
           (sut/get-by-role :listitem [:li "Incorrect email or password."])))
    (is (= [:li "Incorrect email or password."]
           (sut/get-by-role :listitem
                            [:ul#errors.error-messages {:hx-swap-oob true}
                             '([:li "Incorrect email or password."])])))))

(deftest get-text-test
  (is (= "" (sut/get-text nil)))
  (testing "simple case for single node"
    (is (= "hello world" (sut/get-text [:span "hello world"]))))
  (testing "Nested case"
    (is (= "The Rapid Test library helps you test UI components."
           (sut/get-text [:p
                          "The "
                          [:a {:href "#"}
                           [:code "Rapid Test"]]
                          " library helps you test UI components."])))))
