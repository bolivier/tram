(ns tram.utils.plural-test
  (:require [expectations.clojure.test :as e]
            [tram.utils.plural :as sut]))

(def cases
  [["bird" "birds"]])

(e/defexpect pluralize-test

  (doseq [[singular plural] cases]
    (e/expect plural (sut/pluralize singular))
    (e/expect singular (sut/singularize plural))))


(let [word "axis"]
  (sut/pluralize word))
