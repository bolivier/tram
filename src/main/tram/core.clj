(ns tram.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def base-database-config
  {:store :database
   :migration-dir "migrations/"
   :migration-table-name "migrations"
   :db {:dbtype "postgresql"
        :dbname nil}})

(defn generate-config
  "Creates default configuration file contents for tram."
  [project-name]
  {:project/name         project-name
   :database/test        (assoc-in base-database-config
                           [:db :dbname]
                           (str project-name "_test"))
   :database/development (assoc-in base-database-config
                           [:db :dbname]
                           (str project-name "_development"))
   :database/prod        (assoc-in base-database-config
                           [:db :dbname]
                           (str project-name "_production"))})

(defn new [& args]
  (prn "Running as main with args:" args))

(defn get-zprint-config []
  (with-open [r (java.io.PushbackReader. (io/reader (io/file ".zprint.edn")))]
    (edn/read r)))

(defn get-env []
  (System/getenv "TRAM_ENV"))

(defn read-config []
  (with-open [r (java.io.PushbackReader. (io/reader (io/file "tram.edn")))]
    (edn/read r)))

(defn get-tram-database-config []
  {:dbtype "postgresql"
   :dbname "tram"})

(defn get-migration-config [env]
  (let [config (read-config)
        db-key (keyword "database" env)]
    (get config db-key)))

(defn get-database-config
  ([]
   (get-database-config "development"))
  ([env]
   (get (get-migration-config env) :db)))

(defn get-database-name [env]
  (get (get-database-config env) :dbname))
