(ns user
  (:require [clojure.tools.namespace.repl :refer [refresh-all set-refresh-dirs]]
            [integrant.core :as ig]
            [integrant.repl :as ir :refer [go halt reset]]
            [sample-app.config :as c]
            [sample-app.core]))

(ir/set-prep! #(ig/prep c/system))
(set-refresh-dirs "src" "dev" "test")

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
