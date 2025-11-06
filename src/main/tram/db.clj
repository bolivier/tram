(ns ^:public tram.db
  "Primary namespace for db operations in Tram."
  (:require
    [cheshire.core :as json]
    [clojure.java.io :as io]
    [migratus.core :as migratus]
    [next.jdbc.date-time]
    [next.jdbc.prepare :as prepare]
    [next.jdbc.result-set :as rs]
    [potemkin :refer [import-vars]]
    [toucan2.connection]
    [toucan2.core]
    [toucan2.delete]
    [toucan2.execute]
    [toucan2.insert]
    [toucan2.instance]
    [toucan2.model]
    [toucan2.protocols]
    [toucan2.save]
    [toucan2.select]
    [toucan2.tools.after-insert]
    [toucan2.tools.after-select]
    [toucan2.tools.after-update]
    [toucan2.tools.before-delete]
    [toucan2.tools.before-insert]
    [toucan2.tools.before-select]
    [toucan2.tools.before-update]
    [toucan2.tools.compile]
    [toucan2.tools.debug]
    [toucan2.tools.default-fields]
    [toucan2.tools.hydrate]
    [toucan2.tools.named-query]
    [toucan2.tools.transformed]
    [toucan2.update]
    [tram.associations]
    [tram.generators.sql-migration]
    [tram.language :as lang]
    [tram.tram-config :as tram.config]
    [zprint.core :as zpc])
  (:import [org.postgresql.util PGobject]))

(defn pgobj->clj [^org.postgresql.util.PGobject pgobj]
  (let [type  (.getType pgobj)
        value (.getValue pgobj)]
    (case type
      "json"   (json/parse-string value true)
      "jsonb"  (json/parse-string value true)
      "citext" (str value)
      value)))

(extend-protocol rs/ReadableColumn
  java.sql.Array
  (read-column-by-label [^java.sql.Array v _] (vec (.getArray v)))
  (read-column-by-index [^java.sql.Array v _2 _3] (vec (.getArray v)))

  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject pgobj _]
    (pgobj->clj pgobj))
  (read-column-by-index [^org.postgresql.util.PGobject pgobj _2 _3]
    (pgobj->clj pgobj)))

(defn clj->jsonb [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (json/generate-string value))))

(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [^clojure.lang.IPersistentMap v
                  ^java.sql.PreparedStatement stmt
                  ^long idx]
    (.setObject stmt idx (clj->jsonb v)))

  clojure.lang.IPersistentVector
  (set-parameter [^clojure.lang.IPersistentVector v
                  ^java.sql.PreparedStatement stmt
                  ^long idx]
    (let [conn      (.getConnection stmt)
          meta      (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta idx)]
      (if-let [elem-type (when (= (first type-name) \_)
                           (apply str
                             (rest type-name)))]
        (.setObject stmt idx (.createArrayOf conn String (to-array v)))
        (.setObject stmt idx (clj->jsonb v))))))

(import-vars [tram.associations has-many! has-one!]
             [tram.generators.sql-migration write-to-migration-files])

;; toucan2.core
(import-vars
  [toucan2.connection do-with-connection with-connection with-transaction]
  [toucan2.delete delete!]
  [toucan2.execute query query-one reducible-query with-call-count]
  [toucan2.insert
   insert!
   insert-returning-instance!
   insert-returning-pk!
   insert-returning-instances!
   insert-returning-pks!]
  [toucan2.instance instance instance-of? instance?]
  [toucan2.model
   default-connectable
   primary-key-values-map
   primary-keys
   resolve-model
   select-pks-fn
   table-name]
  [toucan2.protocols changes current model original]
  [toucan2.save save!]
  [toucan2.select
   count
   exists?
   reducible-select
   select
   select-fn->fn
   select-fn->pk
   select-fn-reducible
   select-fn-set
   select-fn-vec
   select-one
   select-one-fn
   select-one-pk
   select-pk->fn
   select-pks-set
   select-pks-vec]
  [toucan2.tools.after-insert define-after-insert]
  [toucan2.tools.after-select define-after-select]
  [toucan2.tools.after-update define-after-update]
  [toucan2.tools.before-delete define-before-delete]
  [toucan2.tools.before-insert define-before-insert]
  [toucan2.tools.before-select define-before-select]
  [toucan2.tools.before-update define-before-update]
  [toucan2.tools.default-fields define-default-fields]
  [toucan2.tools.hydrate
   batched-hydrate
   hydrate
   model-for-automagic-hydration
   simple-hydrate]
  [toucan2.tools.named-query define-named-query]
  [toucan2.tools.transformed deftransforms transforms]
  [toucan2.update
   reducible-update
   reducible-update-returning-pks
   update!
   update-returning-pks!])

(defn init-migrations []
  (migratus/init (tram.config/get-migration-config)))

(defn migrate
  "Do pending database migrations.  Runs for the db based on TRAM_ENV. "
  []
  (migratus/migrate (tram.config/get-migration-config)))

(defn migrate-from-cli
  "This is used to call `migrate` from the cli, which passes an arg. It should
  not be used by you."
  [_]
  (try
    (init-migrations)
    (catch Exception _
      (println "Skipping initialize")))
  (migrate))

(defn create-migration
  "Create a new migration"
  [name]
  (migratus/create (tram.config/get-migration-config) name))

(defn rollback-migration []
  (migratus/rollback (tram.config/get-migration-config)))

(defn reset-migrations []
  (migratus/reset (tram.config/get-migration-config)))

(defn init-seeders []
  (migratus/init (tram.config/get-seed-config)))

(defn seed []
  (migratus/migrate (tram.config/get-seed-config)))

(defn reset-seeders []
  (migratus/reset (tram.config/get-seed-config)))

(defn create-seeder
  "Create seeder files.

  Creates a edn migration in /resources/seeders/migrations/*.edn
  and a clj seeder implementation in /resources/seeders/impl/*.clj

  Currently the autocreated formatting is sloppy, but it is mostly right."
  [seeder-name]
  (let [seeder-ns            (lang/filename->ns "seeders" "impl" seeder-name)
        seeder-filename      (lang/ns->filename "resources" seeder-ns)
        [migration-filename] (migratus.core/create (tram.config/get-seed-config)
                                                   seeder-name
                                                   :edn)
        migration-filename   (str "resources/seeders/migrations/"
                                  migration-filename)]
    (io/make-parents migration-filename)
    (io/make-parents seeder-filename)
    (spit migration-filename
          (str {:ns           (symbol seeder-ns)
                :up-fn        'up
                :down-fn      'down
                :transaction? false}))
    (spit seeder-filename
          (str (list 'ns (symbol seeder-ns))
               "\n\n" '(defn up [_] nil)
               "\n\n" '(defn down [_] nil)))
    (zpc/zprint-file migration-filename migration-filename migration-filename)
    (zpc/zprint-file seeder-filename seeder-filename seeder-filename)))

(defn seed []
  (migratus.core/migrate (tram.config/get-seed-config)))
