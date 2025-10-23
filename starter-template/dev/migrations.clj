(ns migrations
  "This is a utility namespace you can use to run migrations from the REPL."
  (:require [tram.db :as db]))


(comment
  ;; initialize the database using the 'init.sql' script
  (db/init)
  ;; Create a new migration
  (db/create "migration-name")
  ;; apply pending migrations
  (db/migrate)
  ;; rollback the migration with the latest timestamp
  (db/rollback)
  nil)
