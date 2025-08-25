(ns tram-cli.entry
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hickory.core :as hc]
            [tram-cli.generate :refer [do-generate]]
            [tram-cli.generator.new :refer [render-new-project-template]]))

(def user-project-dir
  (io/file (System/getenv "TRAM_CLI_CALLED_FROM")))

(alter-var-root #'p/*defaults*
                #(assoc %
                   :continue true
                   :dir      user-project-dir))

(defn convert-to-hiccup [args]
  (let [html (or (second args)
                 (str/trim (:out (p/shell {:out :string} "wl-paste"))))]
    (try
      (-> html
          hc/parse-fragment
          first
          hc/as-hiccup
          prn)
      (catch Exception _
        (println "Could not convert into html: ")
        (prn html)))))

(defn -main [& args]
  (let [cmd (first (mapcat #(str/split % #" ") args))]
    (case cmd
      "new" (render-new-project-template (first (rest args)))
      "g" (do-generate (rest args))
      "generate" (do-generate (rest args))
      "test:watch"
      (if (fs/exists? (io/file user-project-dir
                               "bin/kaocha"))
        (do (println "Watching tests with bin/kaocha")
            (p/shell "bin/kaocha --watch"))
        (do
          (println
            "bin/kaocha not found, invoking clojure command `clojure -X:test:watch`")
          (p/shell "clojure -X:test:watch")))

      "test"
      (if (fs/exists? (io/file user-project-dir
                               "bin/kaocha"))
        (do (println "Running tests with bin/kaocha")
            (p/shell "bin/kaocha"))
        (do
          (println
            "bin/kaocha not found, invoking clojure command `clojure -M:test`")
          (p/shell {:continue true}
                   "clojure -M:test")))

      ;; These are both acceptable names
      "hiccup" (convert-to-hiccup args)
      "html" (convert-to-hiccup args)
      "dev"
      (do
        (println "Starting development environment...")
        (spit (str (System/getenv "TRAM_CLI_CALLED_FROM")
                   "/.nrepl-port")
              "8777")
        (let
          [nrepl-future
           (future
             (p/shell
              ;; TODO: parameterize these versions and probably even the whole command.
              "clojure -Sdeps '{:deps {nrepl/nrepl {:mvn/version \"1.3.1\"} cider/cider-nrepl {:mvn/version \"0.55.7\"} refactor-nrepl/refactor-nrepl {:mvn/version \"3.10.0\"}} :aliases {:cider/nrepl {:main-opts [\"-m\" \"nrepl.cmdline\" \"--middleware\" \"[refactor-nrepl.middleware/wrap-refactor,cider.nrepl/cider-middleware]\"]}}}' -M:dev:test:cider/nrepl"))

           tailwind-future (when (fs/exists? (str user-project-dir
                                                  "/resources/tailwindcss"))
                             (future (p/shell {:dir (str
                                                      user-project-dir
                                                      "/resources/tailwindcss")}
                                              "npm run dev")))]
          @nrepl-future
          (when tailwind-future
            @tailwind-future)))

      (p/shell (str "clojure -M:tram " (str/join " " args))))))
