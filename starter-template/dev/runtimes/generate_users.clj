(ns runtimes.generate-users
  (:require [tram.db :refer [migrate write-to-migration-files]]))


(def blueprint
  "This blueprint was created via the Tram cli.

  Instead of guessing syntax there to get things how you want them, just edit
   this directly and write and rewrite the migration file as needed."
  {:timestamp      "20250701134349"
   :migration-name "create-table-users"
   :actions        [{:type       :create-table
                     :table      "users"
                     :timestamps true
                     :attributes [{:type      :citext
                                   :required? true
                                   :unique?   true
                                   :name      :email}
                                  {:type      :text
                                   :required? true
                                   :name      :password}
                                  {:type      :text
                                   :required? true
                                   :unique?   true
                                   :index?    true
                                   :name      :username}]}]})

(comment
  ;; run this to re/write the migration file
  (write-to-migration-files blueprint)
  (migrate)
  nil)
