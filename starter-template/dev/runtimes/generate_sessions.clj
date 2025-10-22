(ns runtimes.generate-sessions
  (:require [tram.db :refer [migrate write-to-migration-files]]))


(def blueprint
  "This blueprint was created via the Tram cli.

  Instead of guessing syntax there to get things how you want them, just edit
   this directly and write and rewrite the migration file as needed."
  {:model          :sessions
   :template       :model
   :timestamp      "20250701134353"
   :table          :sessions
   :migration-name "create-table-sessions"
   :attributes     [{:name :id
                     :type :primary-key}
                    {:type      :timestamptz
                     :required? true
                     :name      :expires-at}
                    {:type      :reference
                     :name      :user-id
                     :required? true}
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
  (migrate)
  nil)
