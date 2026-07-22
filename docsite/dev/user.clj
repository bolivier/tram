(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh-all set-refresh-dirs]]
            [integrant.core :as ig]
            [integrant.repl :as ir :refer [go halt reset]]
            [tram-docs.config :as c]
            [tram-docs.core]))

(ir/set-prep! #(ig/prep c/system))
(set-refresh-dirs "src" "dev" "test" "../../src/")

(defn restart []
  (reset))

(comment
  (refresh-all)
  (go)
  (halt)
  (slurp "../../")
  (reset)
  nil)

(comment
  ;; How to use morse - an inspector
  (require '[dev.nu.morse :as morse])
  (morse/launch-in-proc)
  (morse/inspect :foo))
