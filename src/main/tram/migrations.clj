(ns tram.migrations
  "This namespace is a convenience namesapce for tram users who want access to
  migratus functions."
  (:require [migratus.core]
            [taoensso.telemere :as t]
            [tram.core :as tram]))

(defn init []
  (migratus.core/init (tram/get-migration-config)))

(defn migrate
  "Do pending database migrations.  Runs for the db based on TRAM_ENV. "
  []
  (let [migration-config (tram/get-migration-config)]
    (t/event! :db/migration
              {:level :info
               :id    :db/migrating
               :data  {:config migration-config}})
    (migratus.core/migrate migration-config)))

(defn create
  "Create a new migration"
  [name]
  (migratus.core/create (tram/get-migration-config) name))

(defn rollback []
  (migratus.core/rollback (tram/get-migration-config)))
