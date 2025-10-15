(ns rapid-test.core
  (:require [clojure.java.io :refer [make-parents]]
            [clojure.string :as str]
            [clojure.test :as t]))

(defmacro with-stub
  "Stubs a function in `body`. Without config, the stubbed fn will return `nil`.
  There are configuration options to change that.

  Each function is in a binding vector, where the bound symbol is the calls that
  are sent to the fn.

  An example:

  (with-stub [db-calls my-db-fn
              fetch-calls {:fn      my-fetch-fn
                           :returns {:status 200}}]
    (my-test-fn))


  If you want to use options, you must pass a map where the key `:fn` is the
  function to stub.

  - `:returns` will make fn return the value you set.
  - `:impl` will replace the implementation of the fn with the one provided."
  [bindings & body]
  (let [stubs        (partition 2 bindings)
        ;; Process each stub into normalized options with generated syms
        stub-configs (map (fn [[calls-sym fn-or-opts]]
                            (let [calls-atom-sym (gensym "calls-val-")
                                  return-sym     (gensym "return-value-")
                                  impl-sym       (gensym "impl-")]
                              (if (map? fn-or-opts)
                                (assoc fn-or-opts
                                  :calls-sym      calls-sym
                                  :calls-atom-sym calls-atom-sym
                                  :return-sym     return-sym
                                  :impl-sym       impl-sym)
                                {:fn             fn-or-opts
                                 :calls-sym      calls-sym
                                 :calls-atom-sym calls-atom-sym
                                 :return-sym     return-sym
                                 :impl-sym       impl-sym})))
                       stubs)
        ;; Generate let bindings for calls atoms and return/impl values
        let-bindings (mapcat (fn [opts] [(:return-sym opts)
                                         (:returns opts)
                                         (:impl-sym opts)
                                         (:impl opts)
                                         (:calls-atom-sym opts)
                                         `(atom [])
                                         (or (:calls-sym opts) (gensym))
                                         (:calls-atom-sym opts)])
                       stub-configs)
        ;; Generate with-redefs pairs
        redef-pairs  (mapcat (fn [opts] [(:fn opts)
                                         `(fn [& args#]
                                            (swap! ~(:calls-atom-sym opts) conj
                                              {:func ~(:fn opts)
                                               :args args#})
                                            (cond
                                              (some? ~(:return-sym opts))
                                              ~(:return-sym opts)

                                              (some? ~(:impl-sym opts))
                                              (apply ~(:impl-sym opts) args#)

                                              :else nil))])
                       stub-configs)]
    `(let [~@let-bindings]
       (with-redefs [~@redef-pairs]
         ~@body))))



(defn match-snapshot
  "Check if the contents from sut are a match to the snapshot matching `snapshot-name`.

  `snapshot-name` should be a fully qualified keyword that will correspond to a
  file at test/snapshots."
  [actual snapshot-name]
  (let [snapshot-filename (str "test/snapshots/"
                               (-> snapshot-name
                                   str
                                   (str/replace #"\." "/")
                                   (str/replace #"^:" "")
                                   (str ".snapshot")))
        snapshot-contents (try
                            (slurp snapshot-filename)
                            (catch Exception e
                              (println "Could not find snapshot"
                                       snapshot-name
                                       "so it was created.")
                              (make-parents snapshot-filename)
                              (spit snapshot-filename actual)
                              actual))]
    (t/is (= snapshot-contents actual)
          (str "Snapshot " snapshot-name " did not match."))))
