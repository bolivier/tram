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

(alter-var-root #'p/*defaults* #(assoc % :continue true))

(defn -main [& args]
  (let [cmd (first args)]
    (case cmd
      "new" (render-new-project-template (first (rest args)))
      "test:watch"
      (if (fs/exists? "bin/kaocha")
        (do (println "Watching tests with bin/kaocha")
            (p/shell "bin/kaocha --watch"))
        (do
          (println
            "bin/kaocha not found, invoking clojure command `clojure -X:test:watch`")
          (p/shell "clojure -X:test:watch")))

      "test"
      (if (fs/exists? "bin/kaocha")
        (do (println "Running tests with bin/kaocha")
            (p/shell "bin/kaocha"))
        (do
          (println
            "bin/kaocha not found, invoking clojure command `clojure -X:test`")
          (p/shell "clojure -X:test")))

      "db:migrate" (p/shell "clojure -M:tram db:migrate")
      (println "Unknown command" cmd)
      #_(client/send {:op   :tram/op
                      :cmd  cmd
                      :args (rest args)}))))
