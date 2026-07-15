(ns sample-app.models.session
  (:require [java-time.api :as jt]
            [tram.db :as db]))

(def ^:private sqlite-datetime-format
  "SQLite has no date type. Handing the driver a LocalDateTime stores epoch
  millis, which no SQLite date function can read; this is the TEXT format
  datetime() emits and understands, so stored values compare against it."
  "yyyy-MM-dd HH:mm:ss")

(db/define-before-insert
  :models/sessions
  [session]
  (assoc session
    :expires-at (jt/format sqlite-datetime-format
                           (-> (jt/local-date-time)
                               (jt/plus (jt/days 2))))))
