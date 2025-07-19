(ns tram.generators.runtime-template-test
  (:require [expectations.clojure.test :as e]
            [tram.generators.runtime-template :as sut]
            [tram.test-fixtures :as fixtures]))

(fixtures/with-tram-config
  (e/defexpect get-ns-for-template
    (e/expect "runtimes.generate-users" (sut/get-runtime-ns fixtures/blueprint))

    (e/expect "dev/runtimes/generate_users.clj"
              (sut/get-runtime-filename fixtures/blueprint))))

(fixtures/with-tram-config
  (e/defexpect template
    (e/expect "tram/templates/model.clj.template"
              (sut/get-template fixtures/blueprint))))
