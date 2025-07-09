(ns sample-app.models.session
  (:require [java-time.api :as jt]
            [toucan2.core :as t2]))

(t2/define-before-insert :models/sessions [session]
  (assoc session
    :expires-at (-> (jt/local-date-time)
                    (jt/plus (jt/days 2)))))
