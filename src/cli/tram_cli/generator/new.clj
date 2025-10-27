(ns tram-cli.generator.new
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn get-bin-file-paths
  "Get string relative path of all files in /bin."
  []
  (->> (fs/list-dir "starter-template/bin")
       (map #(fs/relativize "starter-template" %))
       (map str)))


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

(defn validate-git-installed!
  "Validates that git is installed."
  []
  (when-not (-> "git help"
                p/process
                deref
                :exit
                zero?)
    (throw (ex-info "Git is required to generate a new Tram project."
                    {:error  :git-missing
                     :advice "Please install git"}))))

(defn download-starter-template
  "Downloads the Tram repo to a temp file.

  TODO: cache this"
  []
  (let [download-dir (io/file (str (fs/temp-dir)))]
    (fs/delete-tree (io/file download-dir "tram"))
    (try
      @(p/process {:dir download-dir}
                  "git clone https://github.com/bolivier/tram.git")
      (catch Exception _
        (println "Could not download Tram starter template.")
        (System/exit 1)))
    (io/file download-dir "tram" "starter-template")))

(defn render-new-project-template [project-name]
  (validate-project-name! project-name)
  (validate-git-installed!)
  (let [project-root  (io/file called-from-dir project-name)
        template-root (download-starter-template)]
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
      (doseq [bin (get-bin-file-paths)]
        (fs/copy (io/file template-root bin)
                 (io/file project-root bin)
                 {:copy-attributes  true
                  :replace-existing true})
        (spit (io/file project-root bin)
              (-> (io/file project-root bin)
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
