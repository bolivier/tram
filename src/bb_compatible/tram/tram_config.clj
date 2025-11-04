(ns tram.tram-config
  "Utilities for the tram.edn config file."
  (:require [aero.core :refer [read-config]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn get-zprint-config []
  (with-open [r (java.io.PushbackReader. (io/reader (io/file ".zprint.edn")))]
    (edn/read r)))

(defn get-env []
  (or (System/getProperty "TRAM_ENV")
      (System/getenv "TRAM_ENV")
      (throw (ex-info "No TRAM_ENV set. "
                      {:error   "No TRAM_ENV set."
                       :options #{"development" "test" "production"}}))))

(defn get-tram-config []
  (read-config "tram.edn"))

(defn get-migration-config
  ([]
   (get-migration-config (get-env)))
  ([env]
   (let [config    (get-tram-config)
         db-key    (keyword "database" (name env))
         db-config (get config db-key)]
     (when-not db-config
       (throw (ex-info "Could not find database configuration from tram.edn."
                       {:env env})))
     db-config)))

(defn get-database-config
  ([]
   (get-database-config (get-env)))
  ([env]
   (get (get-migration-config env) :db)))

(defn get-seed-config []
  (merge (get-migration-config)
         {:init-script   "init.sql"
          :migration-dir "seeders/migrations"
          :migration-table-name "seeds"
          :store         :database}))
