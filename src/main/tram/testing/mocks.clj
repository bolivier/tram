(ns tram.testing.mocks)

(def ^:dynamic *calls*
  (atom nil))

(def tram-config
  {:database/development {:db {:dbname "tram_sample_development"
                               :dbtype "postgresql"
                               :host   "localhost"
                               :port   5432
                               :user   "brandon"}
                          :migration-dir "migrations/"
                          :migration-table-name "migrations"
                          :store :database}
   :database/prod        {:db {:dbname "tram_sample_production"
                               :dbtype "postgresql"}
                          :migration-dir "migrations/"
                          :migration-table-name "migrations"
                          :store :database}
   :database/test        {:db {:dbname "tram_sample_test"
                               :dbtype "postgresql"
                               :host   "localhost"
                               :port   5432
                               :user   "brandon"}
                          :migration-dir "migrations/"
                          :migration-table-name "migrations"
                          :store :database}
   :project/name         "tram-sample"})

(defmacro with-temp-ns
  [ns-name & body]
  `(do (create-ns '~ns-name)
       (binding [*ns* (find-ns '~ns-name)]
         (require '[clojure.core :refer :all])
         ~@(map (fn [form]
                  `(eval '~form))
             body))))
