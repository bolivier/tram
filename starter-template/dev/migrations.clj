(ns migrations
  "This is a utility namespace you can use to run migrations from the REPL."
  (:require [tram.migrations :as m]))


(comment
  ;; initialize the database using the 'init.sql' script
  (m/init)
  ;; Create a new migration
  (m/create "rearrange-users-to-add-teams")
  ;; apply pending migrations
  (m/migrate)
  ;; rollback the migration with the latest timestamp
  (m/rollback)
  nil)
