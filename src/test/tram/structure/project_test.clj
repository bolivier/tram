(ns tram.structure.project-test
  (:require [expectations.clojure.test :as e]
            [tram.structure.project :as sut]))

(defmacro with-mock-tram-config
  [binding & body]
  (let
    [mock-config
     (case (count binding)
       1 (first binding)
       2 (second binding)
       (throw
         (ex-info
           "passed bad number of bindings to `with-mock-tram-config`.  Either use 1 or 2 bindings."
           {})))]
    `(with-redefs [tram.core/get-tram-config (constantly ~mock-config)]
       ~@body)))

(e/defexpect finding-a-file
  (with-mock-tram-config [{:project-name :sample-project}]
    (e/expect "src/main/sample-project/models/account.clj"
              (sut/model-file :accounts))))
