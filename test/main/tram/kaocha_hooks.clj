(ns tram.kaocha-hooks
  (:require [matcher-combinators.test]))

(defn load-matcher-combinators
  "Presently this only returns the test because requiring
  `matcher-combinators.test` is done globally, but the kaocha hook requires a
  fn."
  [test test-plan]
  test)
