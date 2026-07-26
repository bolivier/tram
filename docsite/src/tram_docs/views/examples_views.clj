(ns tram-docs.views.examples-views
  (:require [rhizome.core :as rz]
            [tram.routes :as tr]))

(defn global-button [count]
  [:button#global-counter.btn.primary {::rz/on
                                       {:op :http/post
                                        :on :event/click
                                        :http/url
                                        "/rhizome/examples/counter/global"}}
   (str "Increment Global: " count)])

(defn user-button [count]
  [:button#user-counter.btn.primary {::rz/on {:op :http/post
                                              :on :event/click
                                              :http/url
                                              "/rhizome/examples/counter/user"}}
   (str "Increment User: " count)])

(defn counter-example [{:keys [global-count user-count]}]
  [:div {::rz/on {:op       :http/get
                  :http/url "/events"
                  :ident    [:this]}}
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
    ]]
   [:section#examples-content children]])


(defn row [person]
  [:tr.person-row
   [:td (:name person)]
   [:td (:email person)]
   [:td
    [:button.btn {::rz/on {:op       :http/get
                           :http/url (tr/make-route :route/examples.do-edit-row
                                                    {:id (:id person)})}}
     "Edit"]]])

(defn active-edit-row [person]
  [:tr.person-row
   [:td
    [:input {:name  :name
             :form  "edit-person-form"
             :value (:name person)}]]
   [:td
    [:input {:name  :email
             :form  "edit-person-form"
             :value (:email person)}]]
   [:td
    [:div.flex.gap-2
     [:button.btn {::rz/on {:op       :http/get
                            :http/url :route/examples.edit-row}}
      "Cancel"]
     [:button.btn.btn-primary {:type :submit
                               :form "edit-person-form"}
      "Save"]]]])

(defn row
  ([props person]
   [:tr.person-row
    [:td
     (:name person)]
    [:td (:email person)]
    [:td
     [:button.btn {:disabled (:disabled props)
                   ::rz/on   {:op       :http/get
                              :http/url (tr/make-route
                                          :route/examples.do-edit-row
                                          {:id (:id person)})}}
      "Edit"]]])
  ([person]
   (row {} person)))

(defn edit-row-example [locals]
  (let [edit-id (:editable locals)]
    [:div#edit-row-example
     (when edit-id
       [:form#edit-person-form {::rz/on {:op       :http/patch
                                         :http/url (tr/make-route
                                                     :route/examples.do-edit-row
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
