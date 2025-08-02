(ns tram-tasks.runner-test
  (:require [expectations.clojure.test :as e]
            [tram-tasks.runner :as sut]))

(e/defexpect normalization
  (e/expect [:generate :migration]
            (sut/normalize-args (list "generate" "migration" "add-accounts")))

  (e/expect [:generate :migration]
            (sut/normalize-args (list "g" "migration" "add-accounts")))

  (e/expect [:dev] (sut/normalize-args (list "dev")))

  (e/expect [:db :migrate] (sut/normalize-args (list "db" "migrate"))))
