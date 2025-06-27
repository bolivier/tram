(ns tram.core
  (:require [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [malli.core :as m]))

(defn lower-case?
  "Returns `s` if `s` consists of no capital letters [A-Z].

  Returns `nil` otherwise
  
  Other non alphanumeric chars are allowd."
  [s]
  (re-matches #"[^A-Z]*" s))

(defn snake-case?
  "Returns `s` if `s` is a snake case variable."
  [s]
  (= (->snake_case s) s))

(def DatabaseConnectionSchema
  [:map
   [:store [:enum :database]]
   [:migration-dir string?]
   [:migration-table-name string?]
   [:db
    [:map
     [:dbtype [:enum "postgresql"]]
     [:dbname [:and :string [:fn snake-case?] [:fn lower-case?]]]]]])

(def DatabaseConfigSchema
  [:map
   [:project/name string?]
   [:database/test DatabaseConnectionSchema]
   [:database/development DatabaseConnectionSchema]
   [:database/production DatabaseConnectionSchema]])

(defn valid-config? [config]
  (m/validate DatabaseConfigSchema config))

(def base-database-config
  {:store :database
   :migration-dir "migrations/"
   :migration-table-name "migrations"
   :db {:dbtype "postgresql"
        :dbname nil}})

(defn generate-config
  "Creates default configuration file contents for tram."
  [project-name]
  (let [db-safe-project-name (->snake_case project-name)]
    {:project/name         project-name
     :database/test        (assoc-in base-database-config
                             [:db :dbname]
                             (str db-safe-project-name "_test"))
     :database/development (assoc-in base-database-config
                             [:db :dbname]
                             (str db-safe-project-name "_development"))
     :database/production  (assoc-in base-database-config
                             [:db :dbname]
                             (str db-safe-project-name "_production"))}))

(defn new [& args]
  (prn "Running as main with args:" args))

(defn get-zprint-config []
  (with-open [r (java.io.PushbackReader. (io/reader (io/file ".zprint.edn")))]
    (edn/read r)))

(defn get-env []
  (System/getenv "TRAM_ENV"))

(defn get-tram-config []
  (with-open [r (java.io.PushbackReader. (io/reader (io/file "tram.edn")))]
    (edn/read r)))

(defn get-tram-database-config []
  {:dbtype "postgresql"
   :dbname "tram"})

(defn get-migration-config [env]
  (let [config (get-tram-config)
        db-key (keyword "database" env)]
    (get config db-key)))

(defn get-database-config
  ([]
   (get-database-config "development"))
  ([env]
   (get (get-migration-config env) :db)))

(defn get-database-name [env]
  (get (get-database-config env) :dbname))
