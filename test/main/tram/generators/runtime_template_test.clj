(ns tram.generators.runtime-template-test
  (:require [clojure.test :refer [deftest is]]
            [tram.generators.runtime-template :as sut]
            [tram.test-fixtures :as fixtures]))

(deftest get-ns-for-template
  (is (= "runtimes.generate-users" (sut/get-runtime-ns fixtures/blueprint)))
  (is (= "dev/runtimes/generate_users.clj"
         (sut/get-runtime-filename fixtures/blueprint))))

(deftest getting-template-name
  (is (= "tram/templates/model.clj.template"
         (sut/get-template fixtures/blueprint))))
