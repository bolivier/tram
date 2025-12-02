(ns kaocha-utils.kaocha-hooks
  (:require
    [matcher-combinators.test]
    ;; On BB, splice in nothing. On JVM Clojure, pull in jdbc/migratus/tram.
    #?@(:bb []
        :default [[migratus.core :as migratus]
                  [next.jdbc :as jdbc]
                  [tram.tram-config :as tram.config]])))


(defn load-matcher-combinators
  "Presently this only returns the test because requiring
  `matcher-combinators.test` is done globally, but the kaocha hook requires a
  fn."
  [test]
  test)

(defonce seeded?
  (atom false))

(defn set-up-test-database [test]
  #?@(:bb
      [test]                            ; no-op on babashka
      :default
      [(do
         (when-not @seeded?
           (let [migration-config (tram.config/get-migration-config "test")
                 config           (:db migration-config)]

             (try
               ;; Ensure we run CREATE DATABASE against a management DB.
               (jdbc/execute! (jdbc/get-datasource (assoc config :dbname "postgres"))
                              ["CREATE DATABASE tram_test"])
               (catch org.postgresql.util.PSQLException _
                 ;; most likely already exists
                 nil))
             (try
               (let [ds (jdbc/get-datasource (assoc config :dbname "tram_test"))]
                 (jdbc/execute! ds ["DROP SCHEMA public CASCADE"])
                 (jdbc/execute! ds ["CREATE SCHEMA public"]))
               (catch Exception e
                 (println "Could not drop public schema.")
                 nil))
             (migratus/init migration-config)
             (migratus/reset migration-config)
             (reset! seeded? true)))
         test)]))

(comment
  (set-up-test-database nil))
