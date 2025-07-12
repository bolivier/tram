(ns tram-tasks.runner-test
  (:require [expectations.clojure.test :as e]
            [tram-tasks.runner :as sut]
            [tram.testing.mocks :as mock]))

(e/defexpect normalization
  (e/expect [:generate :migration "add-accounts"]
            (sut/normalize-args (list "generate" "migration" "add-accounts")))

  (e/expect [:generate :migration "add-accounts"]
            (sut/normalize-args (list "g" "migration" "add-accounts")))

  (e/expect [:dev] (sut/normalize-args (list "dev")))

  (e/expect [:generate :component "logo"]
            (sut/normalize-args (list "generate" "component" "logo"))))

(e/defexpect generate-component
  (mock/with-tram-config
    (mock/with-stub [spit]
      (sut/run-task (sut/normalize-args (list "generate" "component" "logo")))
      (e/expect "(ns tram-sample.components.logo)

(defn logo []
  nil)"
                (second (:args (first @mock/*calls*))))
      (e/expect "src/tram_sample/components/logo.clj"
                (first (:args (first @mock/*calls*)))))))
