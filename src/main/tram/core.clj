(ns tram.core
  (:require [camel-snake-kebab.core :refer [->snake_case]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [malli.core :as m]
            [potemkin :refer [import-vars]]
            [tram.http.router :refer [defroutes tram-router]]
            [tram.utils.language :as lang]))


(import-vars [tram.http.router tram-router defroutes])

(def DatabaseConnectionSchema
  [:map
   [:store [:enum :database]]
   [:migration-dir string?]
   [:migration-table-name string?]
   [:db
    [:map
     [:dbtype [:enum "postgresql"]]
     [:dbname [:and :string [:fn lang/snake-case?] [:fn lang/lower-case?]]]]]])

(def DatabaseConfigSchema
  [:map
   [:project/name keyword?]
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
    {:project/name         (keyword project-name)
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

(defn get-migration-config
  ([]
   (get-migration-config (get-env)))
  ([env]
   (let [config (get-tram-config)
         db-key (keyword "database" env)]
     (get config db-key))))

(defn get-database-config
  ([]
   (get-database-config "development"))
  ([env]
   (get (get-migration-config env) :db)))

(defn get-database-name [env]
  (get (get-database-config env) :dbname))
