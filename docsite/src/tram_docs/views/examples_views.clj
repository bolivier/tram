(ns tram-docs.views.examples-views
  (:require [rhizome.core :as rz]
            [tram.routes :as tr]))

(defn global-button [count]
  [:button#global-counter.btn.primary {::rz/click
                                       {:do :http/post
                                        :http/url
                                        "/rhizome/examples/counter/global"}}
   (str "Increment Global: " count)])

(defn user-button [count]
  [:button#user-counter.btn.primary {::rz/click
                                     {:do :http/post
                                      :http/url
                                      "/rhizome/examples/counter/user"}}
   (str "Increment User: " count)])

(defn counter-example [{:keys [global-count user-count]}]
  [:div
   [global-button global-count]
   [user-button user-count]])

(defn layout [children]
  [:main#main
   [:aside#examples-sidebar
    [:ul
     [:li
      [:a {:href :route/examples.counter}
       "Counter"]]
     [:li
      [:a {:href :route/examples.edit-row}
       "Edit Row"]]
     [:li
      [:a {:href :route/examples.active-search}
       "Active Search"]]
     [:li
      [:a {:href :route/examples.click-to-load}
       "Click to Load"]]
     [:li
      [:a {:href :route/examples.click-to-edit}
       "Click to Edit"]]
    ]]
   [:section#examples-content children]])

(defn active-edit-row [person]
  [:tr.person-row
   [:td
    [:input.input {:name  :name
                   :form  "edit-person-form"
                   :value (:name person)}]]
   [:td
    [:input.input {:name  :email
                   :form  "edit-person-form"
                   :value (:email person)}]]
   [:td
    [:div.flex.gap-2
     [:button.btn {::rz/click {:do       :http/get
                               :http/url :route/examples.edit-row}}
      "Cancel"]
     [:button.btn.primary {:type :submit
                           :form "edit-person-form"}
      "Save"]]]])

(defn row
  ([props person]
   [:tr.person-row
    [:td
     (:name person)]
    [:td (:email person)]
    [:td
     [:button.btn {:disabled  (:disabled props)
                   ::rz/click {:do       :http/get
                               :http/url (tr/make-route
                                           :route/examples.do-edit-row
                                           {:id (:id person)})}}
      "Edit"]]])
  ([person]
   (row {} person)))

(defn click-to-load-example [{:keys [queue next-page]}]
  [:div#click-to-load-example
   [:table.table#visa-queue
    [:thead
     [:tr
      [:th "Name"]
      [:th "Nationality"]
      [:th "Papers"]]]
    [:tbody
     (for [refugee queue]
       [:tr.person-row
        [:td (:name refugee)]
        [:td (:nationality refugee)]
        [:td (:papers refugee)]])]]
   (if next-page
     [:button.btn.primary {::rz/click {:do       :http/get
                                       :http/url (tr/make-route
                                                   :route/examples.click-to-load
                                                   {:tram.routes/query
                                                    {:loaded next-page}})}}
      "Load 3 more"]
     [:p "Everyone is on the list. Nobody is on the plane."])])

(defn click-to-edit-example [{:keys [patron editing?]}]
  [:div#click-to-edit-example
   (if editing?
     [:form#patron-form {::rz/submit {:do       :http/patch
                                      :http/url :route/examples.click-to-edit}}
      [:input.input {:name  :name
                     :value (:name patron)}]
      [:input.input {:name  :role
                     :value (:role patron)}]
      [:div.flex.gap-2
       [:button.btn {:type      :button
                     ::rz/click {:do       :http/get
                                 :http/url :route/examples.click-to-edit}}
        "Cancel"]
       [:button.btn.primary {:type :submit}
        "Save"]]]
     [:div
      [:h3 (:name patron)]
      [:p (:role patron)]
      [:button.btn {::rz/click {:do       :http/get
                                :http/url :route/examples.click-to-edit.form}}
       "Edit"]])])

(defn search-results [{:keys [crew]}]
  [:tbody#search-results
   (if (seq crew)
     (for [member crew]
       [:tr.person-row
        [:td (:name member)]
        [:td (:rank member)]
        [:td (:duty member)]])
     [:tr
      [:td {:colspan 3}
       "Nobody by that name is on the roster."]])])

(defn active-search-example [locals]
  [:div#active-search-example
   [:form#crew-search-form
    [:input.input {:autocomplete "off"
                   :name         :query
                   :placeholder  "Search the squadron"
                   ::rz/input    {:do :http/post
                                  :http/url
                                  :route/examples.active-search.results}}]]
   [:table.table#squadron-table
    [:thead
     [:tr
      [:th "Name"]
      [:th "Rank"]
      [:th "Duty"]]]
    [search-results locals]]])

(defn edit-row-example [locals]
  (let [edit-id (:editable locals)]
    [:div#edit-row-example
     (when edit-id
       [:form#edit-person-form
        {::rz/submit {:do       :http/patch
                      :http/url (tr/make-route :route/examples.do-edit-row
                                               {:id edit-id})}}])
     [:table.table#people-table
      [:thead
       [:tr
        [:th "Name"]
        [:th "Email"]
        [:th "Actions"]]]
      (for [person (:people locals)
            :let   [editing? (= edit-id (:id person))]]
        (if editing?
          [active-edit-row person]
          [row {:disabled (some? edit-id)}
           person]))]]))
