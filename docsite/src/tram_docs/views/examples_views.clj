(ns tram-docs.views.examples-views
  (:require [rhizome.core :as rz]
            [tram.routes :as tr]))

(defn demo-card
  "Chrome that marks the running demo off from the prose around it.

  Goes inside an example's morph target, so a swap replaces the demo and
  leaves the card alone."
  [& children]
  [:div.demo-card
   [:p.demo-card-label "Demo"]
   [:div.demo-card-body children]])

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
  [demo-card [global-button global-count] [user-button user-count]])

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
     [:li
      [:a {:href :route/examples.inline-validation}
       "Inline Validation"]]
     [:li
      [:a {:href :route/examples.lazy-load}
       "Lazy Load"]]
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

(defn transit-dossier [{:keys [dossier]}]
  [:dl#transit-dossier.dossier
   (for [{:keys [label value]} dossier]
     [:<>
      [:dt label]
      [:dd value]])])

(defn lazy-load-example [_locals]
  [:div#lazy-load-example
   [demo-card
    [:h3 "The letters of transit"]
    ;; The response carries no ::rz/load, so this fetches once.
    [:dl#transit-dossier.dossier {::rz/load {:do :http/get
                                             :http/url
                                             :route/examples.lazy-load.dossier}}
     [:dt "Status"]
     [:dd "Asking around…"]]]])

(defn applicant-error [errors]
  [:p#applicant-error.field-error (:applicant errors)])

(defn destination-error [errors]
  [:p#destination-error.field-error (:destination errors)])

(defn transit-submit [errors]
  [:button#transit-submit.btn.primary {:type     :submit
                                       :disabled (boolean (seq errors))}
   "Apply for transit"])

(defn transit-validation
  "Everything a keystroke can change, each piece matched by its own id."
  [{:keys [errors]}]
  [:<>
   [applicant-error errors]
   [destination-error errors]
   [transit-submit errors]])

(defn transit-approved [{:keys [approved]}]
  [:div#inline-validation-example
   [demo-card
    [:h3 "Signed by General de Gaulle."]
    [:p
     (str (:applicant approved)
          " is on the plane to "
          (:destination approved)
          ".")]]])

(defn inline-validation-example [{:keys [errors]}]
  [:div#inline-validation-example
   [demo-card
    [:form#transit-form.demo-form {::rz/submit
                                   {:do :http/post
                                    :http/url
                                    :route/examples.inline-validation}}
     [:label.field-label {:for "applicant"}
      "Name"]
     [:input.input {:autocomplete "off"
                    :id           :applicant
                    :name         :applicant
                    ::rz/input    {:do :http/post
                                   :debounce 300
                                   :http/url
                                   :route/examples.inline-validation.check}}]
     [applicant-error errors]
     [:label.field-label {:for "destination"}
      "Destination"]
     [:input.input {:autocomplete "off"
                    :id           :destination
                    :name         :destination
                    ::rz/input    {:do :http/post
                                   :debounce 300
                                   :http/url
                                   :route/examples.inline-validation.check}}]
     [destination-error errors]
     [transit-submit errors]]]])

(defn click-to-load-example [{:keys [queue next-page]}]
  [:div#click-to-load-example
   [demo-card
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
      [:button.btn.primary {::rz/click {:do :http/get
                                        :http/url
                                        (tr/make-route
                                          :route/examples.click-to-load
                                          {:tram.routes/query {:loaded
                                                               next-page}})}}
       "Load 3 more"]
      [:p "Everyone is on the list. Nobody is on the plane."])]])

(defn click-to-edit-example [{:keys [patron editing?]}]
  [:div#click-to-edit-example
   [demo-card
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
        "Edit"]])]])

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
   [demo-card
    [:form#crew-search-form
     [:input.input {:autocomplete "off"
                    :name         :query
                    :placeholder  "Search the squadron"
                    ::rz/input    {:do :http/post
                                   :debounce 300
                                   :http/url
                                   :route/examples.active-search.results}}]]
    [:table.table#squadron-table
     [:thead
      [:tr
       [:th "Name"]
       [:th "Rank"]
       [:th "Duty"]]]
     [search-results locals]]]])

(defn edit-row-example [locals]
  (let [edit-id (:editable locals)]
    [:div#edit-row-example
     [demo-card
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
            person]))]]]))
