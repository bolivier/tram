(ns kaocha-utils.kaocha-hooks
  (:require [matcher-combinators.test]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [tram.core :as tram]))

(defn load-matcher-combinators
  "Presently this only returns the test because requiring
  `matcher-combinators.test` is done globally, but the kaocha hook requires a
  fn."
  [test]
  test)

(defonce seeded?
  (atom false))

(defn set-up-test-database [test]
  (when-not @seeded?
    (let [migration-config (tram/get-migration-config "test")
          config (:db migration-config)]
      (try
        (prn config)
        (jdbc/execute! (jdbc/get-datasource config)
                       ["CREATE DATABASE tram_test"])
        (catch org.postgresql.util.PSQLException _
          ;; already exists most likely
          nil))
      (migratus/init migration-config)
      (migratus/reset migration-config))
    (reset! seeded? true))
  test)
