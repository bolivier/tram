(ns sample-app.models.user
  (:require [sample-app.concerns.authentication :refer [hash-password]]
            [tram.db :as db]))

(db/define-after-select
  :models/users
  [user]
  (dissoc user :password))
(db/define-before-insert
  :models/users
  [user]
  (update user :password hash-password))
