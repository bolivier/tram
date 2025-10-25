(ns dev.server
  (:require [babashka.process :as p]))

(defn -main []
  @(p/process
     {:out :inherit
      :err :inherit}
     (str
       "clojure -Sdeps '"
       '{:aliases
         {:cider/nrepl
          {:main-opts
           ["-m"
            "nrepl.cmdline"
            "--middleware"
            "[refactor-nrepl.middleware/wrap-refactor,cider.nrepl/cider-middleware]"]}}

         :deps {cider/cider-nrepl {:mvn/version "0.55.7"}
                nrepl/nrepl       {:mvn/version "1.3.1"}
                refactor-nrepl/refactor-nrepl {:mvn/version "3.10.0"}}}
       "'  -M:dev:test:cider/nrepl")))
