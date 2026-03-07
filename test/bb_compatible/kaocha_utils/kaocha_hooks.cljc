(ns kaocha-utils.kaocha-hooks
  (:require
    [matcher-combinators.test]
    #?@(:bb []
        :default [[migratus.core :as migratus]
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
      [#_{:clj-kondo/ignore [:redundant-do]}
       (do (when-not @seeded?
           (let [migration-config (tram.config/get-migration-config "test")]
             (migratus/init migration-config)
             (migratus/reset migration-config)
             (reset! seeded? true)))
         test)]))

(comment
  (set-up-test-database nil))
