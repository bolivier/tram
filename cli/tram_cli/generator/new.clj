(ns tram-cli.generator.new
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def called-from-dir
  (io/file (System/getenv "TRAM_CLI_CALLED_FROM")))

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
  (let [project-root  (io/file called-from-dir project-name)
        template-root (io/file "starter-template")]
    (binding [p/*defaults* (assoc p/*defaults*
                             :dir      project-root
                             :continue true)]
      (try
        (fs/create-dir project-root)
        (catch Exception _
          (println (str "Directory "
                        project-root
                        " already exists.  Please remove it and try again."))
          (System/exit 1)))
      (println "Copying files")
      (doseq [src  (->> (file-seq template-root)
                        (filter #(.isFile %)))
              :let [relative (-> (.getPath src)
                                 (str/replace-first #".*starter-template/" "")
                                 (str/replace "sample_app"
                                              (ns->path project-name)))
                    dest     (io/file project-root relative)]]
        (io/make-parents dest)
        (spit dest
              (-> src
                  slurp
                  (str/replace "sample_app" (->snake_case project-name))
                  (str/replace "sample-app" project-name))))
      (doseq [db-script ["bin/db-init"]]
        (fs/copy (io/file template-root db-script)
                 (io/file project-root db-script)
                 {:copy-attributes  true
                  :replace-existing true})
        (spit (io/file project-root db-script)
              (-> (io/file project-root db-script)
                  slurp
                  (str/replace "sample_app" (->snake_case project-name))
                  (str/replace "sample-app" project-name))))
      (println "Initializing a git repo.")
      (p/shell "git init")
      (p/shell "git add .")
      (p/shell "git commit -m 'Initial commit'")
      (p/shell "mise trust")
      (println "Next Steps: ")
      (println "  To initialize your database:")
      (println "    $ docker-compose up")
      (println "    $ bin/db-init")
      (println "")
      (println "  To migrate your new database, run:  $ tram db:migrate")
      (println "  Start your server with $ tram dev"))))
