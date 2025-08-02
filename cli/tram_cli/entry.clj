(ns tram-cli.entry
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]
            [tram-cli.generator.new :refer [render-new-project-template]]))

(alter-var-root #'p/*defaults*
                #(assoc %
                   :continue true
                   :dir      (System/getenv "TRAM_CLI_CALLED_FROM")))

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

      (p/shell (str "clojure -M:tram " (str/join " " args))))))
