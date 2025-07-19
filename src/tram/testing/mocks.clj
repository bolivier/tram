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

(defmacro with-tram-config
  [& body]
  `(with-redefs [tram.core/get-tram-config (constantly ~tram-config)]
     ~@body))

(defmacro with-stub
  [[func opts] & body]
  (let [return-value (:returns opts)
        return-fn    (:return-fn opts)
        args         (gensym)]
    `(let [return-value# ~return-value
           return-fn#    ~return-fn]
       (with-redefs [*calls* (if @*calls*
                               *calls*
                               (atom []))
                     ~func   (fn [& ~args]
                               (swap! *calls* conj
                                 {:func ~func
                                  :args ~args})
                               (cond
                                 ~(some? return-value) return-value#
                                 ~(some? return-fn)    (return-fn# ~args)
                                 :else                 nil))]
         ~@body))))

(defmacro with-temp-ns
  [ns-name & body]
  `(do (create-ns '~ns-name)
       (binding [*ns* (find-ns '~ns-name)]
         (require '[clojure.core :refer :all])
         ~@(map (fn [form]
                  `(eval '~form))
             body))))
