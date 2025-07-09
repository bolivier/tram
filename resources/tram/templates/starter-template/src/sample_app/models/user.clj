(ns sample-app.models.user
  (:require [toucan2.core :as t2]
            [tram.crypto :refer [hash-password]]))

(t2/define-after-select :models/users [user] (dissoc user :password))
(t2/define-before-insert :models/users
                         [user]
                         (update user :password hash-password))

(defn get-user-password
  "Gets the users password."
  [email]
  ;; query with a string to skip after-select above
  (:password (t2/select-one "users" :email email)))
