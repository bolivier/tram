(ns runtimes.generate-users
  (:require [tram.migrations :refer [write-to-migration-files]]))


(def blueprint
  "This blueprint was created via the Tram cli.

  Instead of guessing syntax there to get things how you want them, just edit
   this directly and write and rewrite the migration file as needed."
  {:model          :users
   :template       :model
   :timestamp      "20250701134349"
   :table          :users
   :migration-name "create-table-users"
   :attributes     [{:name :id
                     :type :primary-key}
                    {:type      :citext
                     :required? true
                     :unique?   true
                     :name      :email}
                    {:type      :text
                     :required? true
                     :name      :password}
                    {:name      :created-at
                     :type      :timestamptz
                     :required? true
                     :default   :fn/now}
                    {:name      :updated-at
                     :type      :timestamptz
                     :required? true
                     :default   :fn/now
                     :trigger   :update-updated-at}]})

(comment
  ;; run this to re/write the migration file
  (write-to-migration-files blueprint)
  nil)
