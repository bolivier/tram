(ns tram.utils
  (:require [potemkin :refer [import-vars]]
            [tram.utils.core]))

(import-vars
  [tram.utils.core map-keys map-vals with-same-output evolve first-where])
