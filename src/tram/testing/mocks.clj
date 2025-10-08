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
  [bindings & body]
  (let [stubs (partition 2 bindings)

        ;; Process each stub into normalized options with generated syms
        stub-configs (map (fn [[calls-sym fn-or-opts]]
                            (let [calls-atom-sym (gensym "calls-val-")
                                  return-sym (gensym "return-value-")
                                  impl-sym (gensym "impl-")]
                              (if (map? fn-or-opts)
                                (assoc fn-or-opts
                                       :calls-sym calls-sym
                                       :calls-atom-sym calls-atom-sym
                                       :return-sym return-sym
                                       :impl-sym impl-sym)
                                {:fn fn-or-opts
                                 :calls-sym calls-sym
                                 :calls-atom-sym calls-atom-sym
                                 :return-sym return-sym
                                 :impl-sym impl-sym})))
                          stubs)

        ;; Generate let bindings for calls atoms and return/impl values
        let-bindings (mapcat (fn [opts]
                               [(:return-sym opts) (:returns opts)
                                (:impl-sym opts) (:impl opts)
                                (:calls-atom-sym opts) `(atom [])
                                (or (:calls-sym opts) (gensym)) (:calls-atom-sym opts)])
                             stub-configs)

        ;; Generate with-redefs pairs
        redef-pairs (mapcat (fn [opts]
                              [(:fn opts)
                               `(fn [& args#]
                                  (swap! ~(:calls-atom-sym opts) conj
                                         {:func ~(:fn opts)
                                          :args args#})
                                  (cond
                                    (some? ~(:return-sym opts)) ~(:return-sym opts)
                                    (some? ~(:impl-sym opts)) (apply ~(:impl-sym opts) args#)
                                    :else nil))])
                            stub-configs)]

    `(let [~@let-bindings]
       (with-redefs [~@redef-pairs]
         ~@body))))

(defmacro with-temp-ns
  [ns-name & body]
  `(do (create-ns '~ns-name)
       (binding [*ns* (find-ns '~ns-name)]
         (require '[clojure.core :refer :all])
         ~@(map (fn [form]
                  `(eval '~form))
                body))))
