(ns sample-app.views.authentication-views
  (:require [tram.vars :refer [*current-user*]]))

(defn sign-up [_ctx]
  [:div {:class "max-w-md mx-auto mb-1 mt-10"}
   [:div {:class  "p-6 border rounded shadow bg-blue-50 space-y-6"
          :hx-ext "response-targets"
          "hx-on::after-request" "this.classList.add('animate-shake')"}
    [:h1 {:class "text-2xl"}
     "Create an Account"]
    [:div#error]
    [:form {:hx-post   :route/sign-up
            :hx-target "#error"
            :class     "space-y-4"}
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

(defn sign-in [_ctx]
  [:div {:class "max-w-md mx-auto mb-1 mt-10"}
   [:div {:class  "p-6 border rounded shadow bg-blue-50 space-y-6"
          :hx-ext "response-targets"
          "hx-on::after-request" "this.classList.add('animate-shake')"}
    [:h1 {:class "text-2xl"}
     "Sign In"]
    [:div#error]
    [:form {:hx-post   :route/sign-in
            :hx-target "#error"
            :class     "space-y-4"}
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
  [:div.text-red-500 "Incorrect email or password."])

(defn sign-up-form-error []
  [:div.text-red-500 "User with that email already exists"])

(defn dashboard [ctx]
  [:div
   "Welcome to Tram, "
   (:email *current-user*)
   [:a.border.rounded.p-1 {:href :route/log-out}
    "Log out"]])
