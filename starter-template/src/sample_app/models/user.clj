(ns sample-app.models.user
  (:require [tram.db :as db]
            [tram.utils :refer [hash-password]]))

(db/define-after-select :models/users [user] (dissoc user :password))
(db/define-before-insert :models/users
                         [user]
                         (update user :password hash-password))

(defn get-user-password
  "Gets the users password."
  [email]
  ;; query with a string to skip after-select above
  (:password (db/select-one "users" :email email)))
