(ns sample-app.views.authentication-views
  (:require [rhizome.core :as rz]
            [tram.routes :as tr]
            [tram.vars :refer [*current-user*]]))

(defn sign-up [_locals]
  [:div {:class "max-w-md mx-auto mb-1 mt-10"}
   [:div {:class "p-6 border rounded shadow bg-blue-50 space-y-6"}
    [:h1 {:class "text-2xl"}
     "Create an Account"]
    [:div#error]
    [:form {::rz/submit {:do       :http/post
                         :http/url :route/sign-up}
            :class      "space-y-4"}
     (tr/csrf-hidden-field)
     [:div {:class "flex flex-col space-y-1"}
      [:label {:for   "email"
               :class "text-sm"}
       "Email"]
      [:input {:name     "email"
               :id       "email"
               :type     :email
               :required true
               :class    "border bg-white rounded-sm px-2 py-1"}]]
     [:div {:class "flex flex-col space-y-1"}
      [:label {:for   "password"
               :class ""}
       "Password"]
      [:input {:name     "password"
               :id       "password"
               :type     :password
               :required true
               :class    "border bg-white rounded-sm px-2 py-1"}]]
     [:button
      {:type :submit
       :class
       "w-full rounded-sm py-2 px-4 border-2 text-white bg-blue-600 hover:bg-blue-700 cursor-pointer transition-colors"}
      "Create"]]]])

(defn sign-in [_locals]
  [:div {:class "max-w-md mx-auto mb-1 mt-10"}
   [:div {:class "p-6 border rounded shadow bg-blue-50 space-y-6"}
    [:h1 {:class "text-2xl"}
     "Sign In"]
    [:div#error]
    [:form {::rz/submit {:do       :http/post
                         :http/url :route/sign-in}
            :class      "space-y-4"}
     (tr/csrf-hidden-field)
     [:div {:class "flex flex-col space-y-1"}
      [:label {:for   "email"
               :class "text-sm"}
       "Email"]
      [:input {:name     "email"
               :id       "email"
               :type     :email
               :required true
               :class    "border bg-white rounded-sm px-2 py-1"}]]
     [:div {:class "flex flex-col space-y-1"}
      [:label {:for   "password"
               :class ""}
       "Password"]
      [:input {:name     "password"
               :id       "password"
               :type     :password
               :required true
               :class    "border bg-white rounded-sm px-2 py-1"}]]
     [:button
      {:type :submit
       :class
       "w-full rounded-sm py-2 px-4 border-2 text-white bg-blue-600 hover:bg-blue-700 cursor-pointer transition-colors"}
      "Sign In"]]]])

(defn sign-in-form-error []
  [:div#error.text-red-500 "Incorrect email or password."])

(defn sign-up-form-error []
  [:div#error.text-red-500 "User with that email already exists"])

(defn dashboard [_locals]
  [:div
   "Welcome to Tram, "
   (:email *current-user*)
   [:a.border.rounded.p-1 {:href :route/log-out}
    "Log out"]])
