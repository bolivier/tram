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

(defn sign-up-post-handler [{:keys [body-params]}]
  (if-let [user (register-new-account body-params)]
    (let [session (db/insert-returning-instance! :models/sessions
                                                 {:user-id (:id user)})]
      (set-session-cookie (redirect {:session {:identity (:id user)}}
                                    :route/projects)
                          (:id session)))
    {:status 422
     :body   (views/sign-up-form-error)}))

(defn submit-sign-in-form-handler [req]
  (let [{:keys [body-params]}    req
        {:keys [email password]} body-params]
    (if-let [user (get-authenticated-user email password)]
      (let [session (db/insert-returning-instance! :models/sessions
                                                   {:user-id (:id user)})]
        (set-session-cookie {:status  200
                             :headers {"Hx-Redirect" "/dashboard"}
                             :body    ""}
                            (:id session)))
      {:status 422
       :body   (views/sign-in-form-error)})))

(defn log-out-handler [req]
  (let [{:keys [session-id]} (get-cookie-value req)
        session (db/select-one :models/sessions session-id)]
    (db/delete! :models/sessions :user-id (:user-id session)))
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
     :post sign-up-post-handler}]
   ["/sign-in"
    {:name :route/sign-in
     :get  sign-in
     :post submit-sign-in-form-handler}]
   ["/log-out"
    {:name :route/log-out
     :get  log-out-handler}]])
