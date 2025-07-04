(ns tram-cli.generator.new
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [zprint.core :refer [zprint-file-str]]))

(def tram-zprint-edn
  (with-open [r (java.io.PushbackReader. (io/reader (io/file ".zprint.edn")))]
    (edn/read r)))

(defn format-code [source-string]
  (zprint-file-str source-string ::model-template tram-zprint-edn))

(def called-from-dir
  (io/file (System/getenv "TRAM_CLI_CALLED_FROM")))

(defn replace-placeholder [file project-name]
  (let [contents (slurp file)]
    (str/replace contents "sample-app" project-name)))

(defn ns->path [ns]
  (-> ns
      (str/replace "-" "_")
      (str/replace "." "/")))

(defn validate-project-name! [n]
  (when-not (= (->kebab-case n) n)
    (println "Error: project name must be kebab-case.")
    (System/exit 1)))

(defn render-new-project-template [project-name]
  (validate-project-name! project-name)
  (let [project-root (io/file called-from-dir project-name)]
    (try
      (fs/create-dir project-root)
      (catch Exception _
        (println (str "Directory "
                      project-root
                      " already exists.  Please remove it and try again."))
        (System/exit 1)))
    (println "Copying files")
    (let [template-root (io/file "resources/tram/templates/starter-template")]
      (doseq [src  (->> (file-seq template-root)
                        (filter #(.isFile %)))
              :let [relative
                    (-> (.getPath src)
                        (str/replace-first
                          #".*?resources/tram/templates/starter-template/"
                          "")
                        (str/replace "sample_app" (ns->path project-name)))

                    dest (io/file project-root relative)]]
        (io/make-parents dest)
        (spit dest
              (-> src
                  slurp
                  (str/replace "sample_app" (->snake_case project-name))
                  (str/replace "sample-app" project-name)))))
    (System/exit 0)
    (println "Initializing a git repo.")
    (p/shell "git init")
    (p/shell "git add -a")
    (p/shell "git commit -m 'Initial commit")
    (println "Installing development tools with mise.")
    (p/shell "mise install")
    (println
      "Creating development and test databases (this will fail without a postgres installation)")
    (with-open [r (java.io.PushbackReader. (io/reader (io/file "tram.edn")))]
      (let [tram-config (edn/read r)]
        (edn/read r)
        (p/shell (str "createdb "
                      (-> tram-config
                          :database/development
                          :db
                          :dbname)))
        (p/shell (str "createdb "
                      (-> tram-config
                          :database/test
                          :db
                          :dbname)))
        (p/shell "tram db:migrate")))))
