(ns tram-cli.entry
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [tram-cli.daemon-management
             :refer
             [accepting? kill-daemon restart-daemon start-daemon]]
            [tram-cli.generator.new :refer [render-new-project-template]]
            [tram-cli.nrepl-client :as client]
            [zprint.core :refer [zprint-file-str]]))

(defn -main [& args]
  (let [cmd (first args)]
    (case cmd
      "daemon:start" (start-daemon)
      "daemon:kill" (kill-daemon)
      "daemon:restart" (restart-daemon)
      "daemon:status" (if (accepting?)
                        (println "Running")
                        (println "Stopped"))
      "new" (render-new-project-template (first (rest args)))
      "test:watch"
      (if (fs/exists? "bin/kaocha")
        (do (println "Watching tests with bin/kaocha")
            (p/shell {:continue true}
                     "bin/kaocha --watch"))
        (do
          (println
            "bin/kaocha not found, invoking clojure command `clojure -X:test:watch`")
          (p/shell {:continue true}
                   "clojure -X:test:watch")))

      "test"
      (if (fs/exists? "bin/kaocha")
        (do (println "Running tests with bin/kaocha")
            (p/shell {:continue true}
                     "bin/kaocha"))
        (do
          (println
            "bin/kaocha not found, invoking clojure command `clojure -X:test`")
          (p/shell {:continue true}
                   "clojure -X:test")))

      (client/send {:op   :tram/op
                    :cmd  cmd
                    :args (rest args)}))))
