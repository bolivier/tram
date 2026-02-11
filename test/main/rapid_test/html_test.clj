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
                             '([:li "Incorrect email or password."])]))))
  (testing "simple role mappings"
    (testing "heading"
      (is (= [:h1 "Title"] (sut/get-by-role :heading [:div [:h1 "Title"]])))
      (is (= [:h3 "Sub"] (sut/get-by-role :heading [:div [:h3 "Sub"]]))))
    (testing "img"
      (is (= [:img {:src "a.png"
                    :alt "pic"}]
             (sut/get-by-role :img
                              [:div
                               [:img {:src "a.png"
                                      :alt "pic"}]]))))
    (testing "table / row / cell / columnheader"
      (let [html [:table [:tr [:th "Name"] [:td "Alice"]]]]
        (is (= html (sut/get-by-role :table [:div html])))
        (is (= [:tr [:th "Name"] [:td "Alice"]]
               (sut/get-by-role :row [:div html])))
        (is (= [:td "Alice"] (sut/get-by-role :cell [:div html])))
        (is (= [:th "Name"] (sut/get-by-role :columnheader [:div html])))))
    (testing "separator"
      (is (= [:hr] (sut/get-by-role :separator [:div [:hr]]))))
    (testing "article"
      (is (= [:article "Post"]
             (sut/get-by-role :article [:div [:article "Post"]]))))
    (testing "figure"
      (is (= [:figure [:img {:src "x"}]]
             (sut/get-by-role :figure
                              [:div [:figure [:img {:src "x"}]]]))))
    (testing "navigation"
      (is (= [:nav
              [:a {:href "/"}
               "Home"]]
             (sut/get-by-role :navigation
                              [:div
                               [:nav
                                [:a {:href "/"}
                                 "Home"]]]))))
    (testing "main"
      (is (= [:main "Content"]
             (sut/get-by-role :main [:div [:main "Content"]]))))
    (testing "complementary"
      (is (= [:aside "Sidebar"]
             (sut/get-by-role :complementary [:div [:aside "Sidebar"]]))))
    (testing "dialog"
      (is (= [:dialog "Modal"]
             (sut/get-by-role :dialog [:div [:dialog "Modal"]]))))
    (testing "form"
      (is (= [:form [:input]] (sut/get-by-role :form [:div [:form [:input]]]))))
    (testing "region"
      (is (= [:section "Area"]
             (sut/get-by-role :region [:div [:section "Area"]]))))
    (testing "meter"
      (is (= [:meter {:value 5}]
             (sut/get-by-role :meter
                              [:div [:meter {:value 5}]]))))
    (testing "progressbar"
      (is (= [:progress {:value 50}]
             (sut/get-by-role :progressbar
                              [:div [:progress {:value 50}]]))))
    (testing "option"
      (is (= [:option "A"]
             (sut/get-by-role :option [:select [:option "A"] [:option "B"]]))))
    (testing "search"
      (is (= [:search [:input]]
             (sut/get-by-role :search [:div [:search [:input]]])))))
  (testing "input-type dispatch"
    (testing "button"
      (is (some? (sut/get-by-role :button [:div [:button "Click"]])))
      (is (some? (sut/get-by-role :button
                                  [:div [:input {:type "submit"}]])))
      (is (some? (sut/get-by-role :button
                                  [:div [:input {:type "reset"}]])))
      (is (some? (sut/get-by-role :button
                                  [:div [:input {:type "button"}]]))))
    (testing "textbox"
      (is (some? (sut/get-by-role :textbox [:div [:textarea]])))
      (is (some? (sut/get-by-role :textbox
                                  [:div [:input {:type "text"}]])))
      (is (some? (sut/get-by-role :textbox [:div [:input]])))
      (is (nil? (sut/get-by-role :textbox
                                 [:div [:input {:type "email"}]]))))
    (testing "checkbox"
      (is (some? (sut/get-by-role :checkbox
                                  [:div [:input {:type "checkbox"}]]))))
    (testing "radio"
      (is (some? (sut/get-by-role :radio
                                  [:div [:input {:type "radio"}]]))))
    (testing "searchbox"
      (is (some? (sut/get-by-role :searchbox
                                  [:div [:input {:type "search"}]]))))
    (testing "slider"
      (is (some? (sut/get-by-role :slider
                                  [:div [:input {:type "range"}]]))))
    (testing "spinbutton"
      (is (some? (sut/get-by-role :spinbutton
                                  [:div [:input {:type "number"}]])))))
  (testing "conditional roles"
    (testing "link"
      (is (some? (sut/get-by-role :link
                                  [:div
                                   [:a {:href "/page"}
                                    "Link"]])))
      (is (nil? (sut/get-by-role :link [:div [:a "No href"]]))))
    (testing "banner"
      (is (some? (sut/get-by-role :banner [:div [:header "Site Header"]])))
      (is (nil? (sut/get-by-role :banner
                                 [:div
                                  [:article [:header "Article Header"]]]))))
    (testing "contentinfo"
      (is (some? (sut/get-by-role :contentinfo [:div [:footer "Site Footer"]])))
      (is (nil? (sut/get-by-role :contentinfo
                                 [:div [:nav [:footer "Nav Footer"]]]))))
    (testing "rowheader"
      (is (some? (sut/get-by-role :rowheader
                                  [:table
                                   [:tr
                                    [:th {:scope "row"}
                                     "Name"]]])))
      (is (nil? (sut/get-by-role :rowheader [:table [:tr [:th "Name"]]]))))
    (testing "combobox"
      (is (some? (sut/get-by-role :combobox [:div [:select [:option "A"]]])))
      (is (nil? (sut/get-by-role :combobox
                                 [:div
                                  [:select {:multiple true}
                                   [:option "A"]]])))))
  (testing "explicit role attribute fallback"
    (is (some? (sut/get-by-role :alert
                                [:div
                                 [:div {:role "alert"}
                                  "Error!"]])))
    (is (some? (sut/get-by-role :status
                                [:div
                                 [:div {:role "status"}
                                  "OK"]])))
    (is (some? (sut/get-by-role :switch
                                [:div
                                 [:span {:role "switch"}
                                  "Toggle"]])))
    (is (nil? (sut/get-by-role :alert [:div [:span "No role here"]]))))
  (testing "2-arity backward compatibility"
    (is (= [:h1 "Title"] (sut/get-by-role :heading [:div [:h1 "Title"]]))))
  (testing "returns nil for no match"
    (is (nil? (sut/get-by-role :button [:div [:span "Not a button"]])))))

(deftest get-by-role-options-test
  (testing "name option - exact string match"
    (is (= [:button "Save"]
           (sut/get-by-role :button
                            [:div [:button "Cancel"] [:button "Save"]]
                            {:name "Save"}))))
  (testing "name option - aria-label takes precedence"
    (is (= [:button {:aria-label "Close dialog"}
            "X"]
           (sut/get-by-role :button
                            [:div
                             [:button {:aria-label "Close dialog"}
                              "X"]]
                            {:name "Close dialog"}))))
  (testing "name option - regex"
    (is (= [:button "Save changes"]
           (sut/get-by-role :button
                            [:div [:button "Cancel"] [:button "Save changes"]]
                            {:name #"Save"}))))
  (testing "level option - tag-derived"
    (is (= [:h2 "Subtitle"]
           (sut/get-by-role :heading
                            [:div [:h1 "Title"] [:h2 "Subtitle"]]
                            {:level 2}))))
  (testing "level option - aria-level"
    (is (= [:div {:role       "heading"
                  :aria-level 3}
            "Custom"]
           (sut/get-by-role :heading
                            [:div
                             [:h1 "Title"]
                             [:div {:role       "heading"
                                    :aria-level 3}
                              "Custom"]]
                            {:level 3}))))
  (testing "name and level combined"
    (is (= [:h2 "Features"]
           (sut/get-by-role
             :heading
             [:div [:h2 "About"] [:h2 "Features"] [:h3 "Details"]]
             {:name  "Features"
              :level 2})))))

(deftest get-all-by-role-test
  (testing "returns all matches"
    (is (= [[:li "A"] [:li "B"] [:li "C"]]
           (sut/get-all-by-role :listitem
                                [:ul [:li "A"] [:li "B"] [:li "C"]]))))
  (testing "returns empty vector when no matches"
    (is (= [] (sut/get-all-by-role :button [:div [:span "No buttons"]]))))
  (testing "with options"
    (is (= [[:h2 "One"] [:h2 "Two"]]
           (sut/get-all-by-role
             :heading
             [:div [:h1 "Title"] [:h2 "One"] [:h2 "Two"] [:h3 "Three"]]
             {:level 2}))))
  (testing "2-arity"
    (is (= [[:h1 "A"] [:h2 "B"]]
           (sut/get-all-by-role :heading [:div [:h1 "A"] [:h2 "B"]])))))

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
