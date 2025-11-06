(ns runtimes.generate-sessions
  (:require [tram.db :refer [migrate write-to-migration-files]]))


(def blueprint
  "This blueprint was created via the Tram cli.

  Instead of guessing syntax there to get things how you want them, just edit
   this directly and write and rewrite the migration file as needed."
  {:timestamp      "20250701134353"
   :migration-name "create-table-sessions"
   :actions        [{:type       :create-table
                     :table      "sessions"
                     :timestamps true
                     :attributes [{:type      :reference
                                   :name      :user-id
                                   :required? true}
                                  {:type      :timestamptz
                                   :required? true
                                   :name      :expires-at}]}]})

(comment
  ;; run this to re/write the migration file
  (write-to-migration-files blueprint)
  (migrate)
  nil)
