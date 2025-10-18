(ns tram.core
  (:require [camel-snake-kebab.core :refer [->snake_case]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [malli.core :as m]
            [potemkin :refer [import-vars]]
            [tram.db]
            [tram.http.interceptors :as interceptors]
            [tram.http.router :as router]
            [tram.language :as lang]
            [zprint.core :refer [zprint-file-str]]))

(import-vars [tram.http.router tram-router defroutes])

(def with-layout
  interceptors/layout-interceptor)

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
  (or (System/getProperty "TRAM_ENV")
      (System/getenv "TRAM_ENV")
      (throw (ex-info "No TRAM_ENV set. "
                      {:error   "No TRAM_ENV set."
                       :options #{"development" "test" "production"}}))))

(defn get-tram-config []
  (with-open [r (java.io.PushbackReader. (io/reader (io/file "./tram.edn")))]
    (edn/read r)))

(defn get-tram-database-config []
  {:dbtype "postgresql"
   :dbname "tram"})

(defn get-migration-config
  ([]
   (get-migration-config (get-env)))
  ([env]
   (let [config    (get-tram-config)
         db-key    (keyword "database" env)
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

(defn get-database-name [env]
  (get (get-database-config env) :dbname))

(defn format-source [source]
  (zprint-file-str source ::formatted-source (get-zprint-config)))
