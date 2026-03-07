(ns rapid-test.html-test
  (:require [clojure.test :refer [deftest is testing]]
            [rapid-test.html :as sut]))

(deftest get-by-role-test
  (testing "list role"
    (is (= [:ul#error-list [:li "Something went wrong!"]]
           (sut/get-by-role [:div
                             [:span "errors: "]
                             [:ul#error-list [:li "Something went wrong!"]]]
                            :list))))
  (testing "list item role"
    (is (= nil
           (sut/get-by-role [:li "Incorrect email or password."] :listitem)))
    (is (= [:li "Incorrect email or password."]
           (sut/get-by-role [:ul#errors.error-messages {:hx-swap-oob true}
                             '([:li "Incorrect email or password."])]
                            :listitem))))
  (testing "simple role mappings"
    (testing "heading"
      (is (= [:h1 "Title"] (sut/get-by-role [:div [:h1 "Title"]] :heading)))
      (is (= [:h3 "Sub"] (sut/get-by-role [:div [:h3 "Sub"]] :heading))))
    (testing "img"
      (is (= [:img {:src "a.png"
                    :alt "pic"}]
             (sut/get-by-role [:div
                               [:img {:src "a.png"
                                      :alt "pic"}]]
                              :img))))
    (testing "table / row / cell / columnheader"
      (let [html [:table [:tr [:th "Name"] [:td "Alice"]]]]
        (is (= html (sut/get-by-role [:div html] :table)))
        (is (= [:tr [:th "Name"] [:td "Alice"]]
               (sut/get-by-role [:div html] :row)))
        (is (= [:td "Alice"] (sut/get-by-role [:div html] :cell)))
        (is (= [:th "Name"] (sut/get-by-role [:div html] :columnheader)))))
    (testing "separator"
      (is (= [:hr] (sut/get-by-role [:div [:hr]] :separator))))
    (testing "article"
      (is (= [:article "Post"]
             (sut/get-by-role [:div [:article "Post"]] :article))))
    (testing "figure"
      (is (= [:figure [:img {:src "x"}]]
             (sut/get-by-role [:div [:figure [:img {:src "x"}]]]
                              :figure))))
    (testing "navigation"
      (is (= [:nav
              [:a {:href "/"}
               "Home"]]
             (sut/get-by-role [:div
                               [:nav
                                [:a {:href "/"}
                                 "Home"]]]
                              :navigation))))
    (testing "main"
      (is (= [:main "Content"]
             (sut/get-by-role [:div [:main "Content"]] :main))))
    (testing "complementary"
      (is (= [:aside "Sidebar"]
             (sut/get-by-role [:div [:aside "Sidebar"]] :complementary))))
    (testing "dialog"
      (is (= [:dialog "Modal"]
             (sut/get-by-role [:div [:dialog "Modal"]] :dialog))))
    (testing "form"
      (is (= [:form [:input]] (sut/get-by-role [:div [:form [:input]]] :form))))
    (testing "region"
      (is (= [:section "Area"]
             (sut/get-by-role [:div [:section "Area"]] :region))))
    (testing "meter"
      (is (= [:meter {:value 5}]
             (sut/get-by-role [:div [:meter {:value 5}]]
                              :meter))))
    (testing "progressbar"
      (is (= [:progress {:value 50}]
             (sut/get-by-role [:div [:progress {:value 50}]]
                              :progressbar))))
    (testing "option"
      (is (= [:option "A"]
             (sut/get-by-role [:select [:option "A"] [:option "B"]] :option))))
    (testing "search"
      (is (= [:search [:input]]
             (sut/get-by-role [:div [:search [:input]]] :search)))))
  (testing "input-type dispatch"
    (testing "button"
      (is (some? (sut/get-by-role [:div [:button "Click"]] :button)))
      (is (some? (sut/get-by-role [:div [:input {:type "submit"}]]
                                  :button)))
      (is (some? (sut/get-by-role [:div [:input {:type "reset"}]]
                                  :button)))
      (is (some? (sut/get-by-role [:div [:input {:type "button"}]]
                                  :button))))
    (testing "textbox"
      (is (some? (sut/get-by-role [:div [:textarea]] :textbox)))
      (is (some? (sut/get-by-role [:div [:input {:type "text"}]]
                                  :textbox)))
      (is (some? (sut/get-by-role [:div [:input]] :textbox)))
      (is (nil? (sut/get-by-role [:div [:input {:type "email"}]]
                                 :textbox))))
    (testing "checkbox"
      (is (some? (sut/get-by-role [:div [:input {:type "checkbox"}]]
                                  :checkbox))))
    (testing "radio"
      (is (some? (sut/get-by-role [:div [:input {:type "radio"}]]
                                  :radio))))
    (testing "searchbox"
      (is (some? (sut/get-by-role [:div [:input {:type "search"}]]
                                  :searchbox))))
    (testing "slider"
      (is (some? (sut/get-by-role [:div [:input {:type "range"}]]
                                  :slider))))
    (testing "spinbutton"
      (is (some? (sut/get-by-role [:div [:input {:type "number"}]]
                                  :spinbutton)))))
  (testing "conditional roles"
    (testing "link"
      (is (some? (sut/get-by-role [:div
                                   [:a {:href "/page"}
                                    "Link"]]
                                  :link)))
      (is (nil? (sut/get-by-role [:div [:a "No href"]] :link))))
    (testing "banner"
      (is (some? (sut/get-by-role [:div [:header "Site Header"]] :banner)))
      (is (nil? (sut/get-by-role [:div
                                  [:article [:header "Article Header"]]]
                                 :banner))))
    (testing "contentinfo"
      (is (some? (sut/get-by-role [:div [:footer "Site Footer"]] :contentinfo)))
      (is (nil? (sut/get-by-role [:div [:nav [:footer "Nav Footer"]]]
                                 :contentinfo))))
    (testing "rowheader"
      (is (some? (sut/get-by-role [:table
                                   [:tr
                                    [:th {:scope "row"}
                                     "Name"]]]
                                  :rowheader)))
      (is (nil? (sut/get-by-role [:table [:tr [:th "Name"]]] :rowheader))))
    (testing "combobox"
      (is (some? (sut/get-by-role [:div [:select [:option "A"]]] :combobox)))
      (is (some? (sut/get-by-role [:div
                                   [:select {:size 0}
                                    [:option "A"]]]
                                  :combobox)))
      (is (some? (sut/get-by-role [:div
                                   [:select {:size "1"}
                                    [:option "A"]]]
                                  :combobox)))
      (is (nil? (sut/get-by-role [:div
                                  [:select {:size 3}
                                   [:option "A"]]]
                                 :combobox)))
      (is (nil? (sut/get-by-role [:div
                                  [:select {:size "4"}
                                   [:option "A"]]]
                                 :combobox)))
      (is (nil? (sut/get-by-role [:div
                                  [:select {:multiple true}
                                   [:option "A"]]]
                                 :combobox)))))
  (testing "explicit role attribute fallback"
    (is (some? (sut/get-by-role [:div
                                 [:div {:role "alert"}
                                  "Error!"]]
                                :alert)))
    (is (some? (sut/get-by-role [:div
                                 [:div {:role "status"}
                                  "OK"]]
                                :status)))
    (is (some? (sut/get-by-role [:div
                                 [:span {:role "switch"}
                                  "Toggle"]]
                                :switch)))
    (is (nil? (sut/get-by-role [:div [:span "No role here"]] :alert))))
  (testing "2-arity backward compatibility"
    (is (= [:h1 "Title"] (sut/get-by-role [:div [:h1 "Title"]] :heading))))
  (testing "returns nil for no match"
    (is (nil? (sut/get-by-role [:div [:span "Not a button"]] :button)))))

(deftest get-by-role-options-test
  (testing "name option - exact string match"
    (is (= [:button "Save"]
           (sut/get-by-role [:div [:button "Cancel"] [:button "Save"]]
                            :button
                            {:name "Save"}))))
  (testing "name option - aria-label takes precedence"
    (is (= [:button {:aria-label "Close dialog"}
            "X"]
           (sut/get-by-role [:div
                             [:button {:aria-label "Close dialog"}
                              "X"]]
                            :button
                            {:name "Close dialog"}))))
  (testing "name option - regex"
    (is (= [:button "Save changes"]
           (sut/get-by-role [:div [:button "Cancel"] [:button "Save changes"]]
                            :button
                            {:name #"Save"}))))
  (testing "level option - tag-derived"
    (is (= [:h2 "Subtitle"]
           (sut/get-by-role [:div [:h1 "Title"] [:h2 "Subtitle"]]
                            :heading
                            {:level 2}))))
  (testing "level option - aria-level"
    (is (= [:div {:role       "heading"
                  :aria-level 3}
            "Custom"]
           (sut/get-by-role [:div
                             [:h1 "Title"]
                             [:div {:role       "heading"
                                    :aria-level 3}
                              "Custom"]]
                            :heading
                            {:level 3}))))
  (testing "name and level combined"
    (is (= [:h2 "Features"]
           (sut/get-by-role
             [:div [:h2 "About"] [:h2 "Features"] [:h3 "Details"]]
             :heading
             {:name  "Features"
              :level 2})))))

(deftest get-all-by-role-test
  (testing "returns all matches"
    (is (= [[:li "A"] [:li "B"] [:li "C"]]
           (sut/get-all-by-role [:ul [:li "A"] [:li "B"] [:li "C"]]
                                :listitem))))
  (testing "returns empty vector when no matches"
    (is (= [] (sut/get-all-by-role [:div [:span "No buttons"]] :button))))
  (testing "with options"
    (is (= [[:h2 "One"] [:h2 "Two"]]
           (sut/get-all-by-role
             [:div [:h1 "Title"] [:h2 "One"] [:h2 "Two"] [:h3 "Three"]]
             :heading
             {:level 2}))))
  (testing "2-arity"
    (is (= [[:h1 "A"] [:h2 "B"]]
           (sut/get-all-by-role [:div [:h1 "A"] [:h2 "B"]] :heading)))))

(deftest get-by-label-test
  (testing "label with for attribute"
    (is (= [:input {:id "username"}]
           (sut/get-by-label "Username"
                             [:div
                              [:label {:for "username"} "Username"]
                              [:input {:id "username"}]]))))
  (testing "label with keyword id"
    (is (= [:input#my-id]
           (sut/get-by-label "Username"
                             [:div
                              [:label {:for :my-id} "Username"]
                              [:input#my-id]]))))
  (testing "wrapping label"
    (is (= [:input]
           (sut/get-by-label "Username"
                             [:div
                              [:label "Username" [:input]]]))))
  (testing "aria-label"
    (is (= [:button {:aria-label "Close"} "X"]
           (sut/get-by-label "Close"
                             [:div
                              [:button {:aria-label "Close"} "X"]]))))
  (testing "aria-labelledby"
    (is (= [:input {:aria-labelledby "name-label"}]
           (sut/get-by-label "Full Name"
                             [:div
                              [:span {:id "name-label"} "Full Name"]
                              [:input {:aria-labelledby "name-label"}]]))))
  (testing "returns nil when no label matches"
    (is (nil? (sut/get-by-label "Username"
                                [:div [:input {:id "username"}]]))))
  (testing "returns nil when label text doesn't match"
    (is (nil? (sut/get-by-label "Password"
                                [:div
                                 [:label {:for "username"} "Username"]
                                 [:input {:id "username"}]])))))

(deftest get-all-by-label-test
  (testing "returns all elements with matching label"
    (is (= [[:input {:id "first"}] [:input {:id "second" :aria-label "Name"}]]
           (sut/get-all-by-label "Name"
                                 [:div
                                  [:label {:for "first"} "Name"]
                                  [:input {:id "first"}]
                                  [:input {:id "second" :aria-label "Name"}]]))))
  (testing "returns empty vector when no matches"
    (is (= [] (sut/get-all-by-label "Username" [:div [:input]])))))
