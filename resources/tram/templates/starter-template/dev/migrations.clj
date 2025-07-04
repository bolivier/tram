(ns migrations
  "This is a utility namespace you can use to run migrations from the REPL."
  (:require [tram.migrations :as m]))


(def config
  {})

(defn run-migrations [_]
  (m/migrate config))

(comment
  ;; initialize the database using the 'init.sql' script
  (m/create config "rearrange-users-to-add-teams")
  (m/init)
  (m/init config)
  ;; apply pending migrations
  (m/migrate config)
  ;; rollback the migration with the latest timestamp
  (m/rollback config)
  ;; bring up migrations matching the ids
  (m/up config 20111206154000)
  ;; bring down migrations matching the ids
  (m/down config 20111206154000))

(comment
  (m/create seed-config "add-sample-projects")
  (m/migrate seed-config))
