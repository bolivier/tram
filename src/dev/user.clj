(ns user
  (:require
    [clojure.tools.namespace.repl :refer [clear refresh refresh-all] :as nsr]))

(nsr/set-refresh-dirs "deps/tram/src/main") ; or ["src" "dev"]

(nsr/clear)

;; Then:
(refresh)


                                        ;
