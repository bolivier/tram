(ns sample-app.handlers.authentication-handlers
  (:require [sample-app.concerns.authentication
             :refer
             [clear-session-cookie
              get-authenticated-user
              get-cookie-value
              register-new-account
              set-session-cookie]]
            [sample-app.views.authentication-views :as views]
            [tram.core :refer [defroutes]]
            [tram.db :as db]
            [tram.routes :refer [redirect]]))

(defn sign-up-post-handler [req]
  (if-let [user (register-new-account (get-in req [:parameters :body]))]
    (let [session (db/insert-returning-instance! :models/sessions
                                                 {:user-id (:id user)})]
      (set-session-cookie (redirect {:session {:identity (:id user)}}
                                    :route/dashboard)
                          (:id session)))
    {:status 422
     :body   (views/sign-up-form-error)}))

(defn submit-sign-in-form-handler [req]
  (let [{:keys [email password]} (get-in req [:parameters :body])]
    (if-let [user (get-authenticated-user email password)]
      (let [session (db/insert-returning-instance! :models/sessions
                                                   {:user-id (:id user)})]
        (set-session-cookie (redirect {:session {:identity (:id user)}}
                                      :route/dashboard)
                            (:id session)))
      {:status 422
       :body   (views/sign-in-form-error)})))

(defn log-out-handler [req]
  (let [{:keys [session-id]} (get-cookie-value req)]
    (db/delete! :models/sessions session-id))
  (clear-session-cookie (-> {:session nil}
                            (redirect :route/sign-in))))

(defn sign-in [req]
  (if (some? (:current-user req))
    (redirect :route/dashboard)
    {:status 200}))

(defroutes routes
  [["/sign-up"
    {:name :route/sign-up
     :get  :view/sign-up
     :post {:handler    sign-up-post-handler
            :parameters {:body [:map [:email :string] [:password :string]]}}}]
   ["/sign-in"
    {:name :route/sign-in
     :get  sign-in
     :post {:handler    submit-sign-in-form-handler
            :parameters {:body [:map [:email :string] [:password :string]]}}}]
   ["/log-out"
    {:name :route/log-out
     :get  log-out-handler}]])
