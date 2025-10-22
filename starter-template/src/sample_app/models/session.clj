(ns sample-app.models.session
  (:require [java-time.api :as jt]
            [tram.db :as db]))

(db/define-before-insert :models/sessions
                         [session]
                         (assoc session
                           :expires-at (-> (jt/local-date-time)
                                           (jt/plus (jt/days 2)))))
