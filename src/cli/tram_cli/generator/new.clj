(ns tram-cli.generator.new
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn get-template-file-paths
  "Relative paths of the files making up the template at `template-root`.

  Asks git rather than walking the filesystem, so that a checkout's build
  output — node_modules, target, caches, the odd .DS_Store — stays out of
  generated projects. `--others --exclude-standard` keeps files that are new but
  not ignored, and the existence check drops any that are staged but deleted."
  [template-root]
  (->> (p/shell {:dir template-root
                 :out :string}
                "git ls-files --cached --others --exclude-standard")
       :out
       str/split-lines
       (remove str/blank?)
       (filter #(.isFile (io/file template-root %)))))

(defn get-bin-file-paths
  "Get string relative path of all files under `template-root`'s /bin,
  nested directories like bin/dev included.

  Resolved against `template-root` rather than the working directory because
  outside development mode the template is a clone in a temp dir."
  [template-root]
  (->> (file-seq (io/file template-root "bin"))
       (remove fs/directory?)
       (map #(fs/relativize (str template-root) (str %)))
       (map str)))

(def called-from-dir
  (io/file (System/getenv "TRAM_CLI_CALLED_FROM")))

(defn template-repo-sha
  "HEAD of the repo the template came from."
  [template-root]
  (-> (p/shell {:dir (str template-root)
                :out :string}
               "git rev-parse HEAD")
      :out
      str/trim))

(defn stamp-tram-sha
  "Points the generated deps.edn at `sha`, so the app depends on the exact
  framework commit its template came from.

  The tram coordinate holds the only 40-char hex string in the file."
  [project-root sha]
  (let [deps (io/file project-root "deps.edn")]
    (spit deps (str/replace (slurp deps) #"[0-9a-f]{40}" sha))))

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
  (if (= "true" (System/getenv "TRAM_DEVELOPMENT_MODE"))
    (io/file "starter-template")
    (let [download-dir (io/file (str (fs/temp-dir)))]
      (println "Cloning Tram git repo")
      (fs/delete-tree (io/file download-dir
                               "tram"))
      (try
        (p/shell {:dir download-dir}
                 "git clone https://github.com/bolivier/tram.git")
        (catch Exception _
          (println "Could not download Tram starter template.")
          (System/exit 1)))
      (io/file download-dir
               "tram"
               "starter-template"))))

(defn render-new-project-template [project-name]
  (validate-project-name! project-name)
  (validate-git-installed!)
  (let [project-root  (io/file called-from-dir project-name)
        template-root (download-starter-template)]
    (binding [p/*defaults* (assoc p/*defaults*
                             :dir      project-root
                             :continue true)]
      (println "Creating project dir")
      (try
        (fs/create-dir project-root)
        (catch Exception _
          (println (str "Directory "
                        project-root
                        " already exists.  Please remove it and try again."))
          (System/exit 1)))
      (println "Copying files")
      (doseq [tracked (get-template-file-paths template-root)
              :let    [src      (io/file template-root tracked)
                       relative (str/replace tracked
                                             "sample_app"
                                             (ns->path project-name))
                       dest     (io/file project-root relative)]]
        (io/make-parents dest)
        (spit dest
              (-> src
                  slurp
                  (str/replace "sample_app" (->snake_case project-name))
                  (str/replace "sample-app" project-name))))
      (doseq [bin (get-bin-file-paths template-root)]
        (io/make-parents (io/file project-root bin))
        (fs/copy (io/file template-root bin)
                 (io/file project-root bin)
                 {:copy-attributes  true
                  :replace-existing true})
        (spit (io/file project-root bin)
              (-> (io/file project-root bin)
                  slurp
                  (str/replace "sample_app" (->snake_case project-name))
                  (str/replace "sample-app" project-name))))
      (stamp-tram-sha project-root (template-repo-sha template-root))
      ;; bin/css installs the node deps and builds the stylesheet, so the
      ;; app serves a styled page before `tram dev` starts Tailwind's
      ;; watcher.
      (println "Installing node deps and building CSS")
      (p/shell "bin/css")
      (println "Importing lint configs (this warms the dependency cache too)")
      (p/shell "bin/copy-lint-configs")
      ;; Renaming sample-app to the project's name shifts identifier
      ;; lengths, which zprint aligns on. Without this the first commit is
      ;; already misformatted.
      (println "Formatting")
      (p/shell "bin/format")
      (println "Initializing a git repo.")
      (p/shell "git init")
      (p/shell "git add .")
      (p/shell "git commit -m 'Initial commit'")
      ;; SQLite keeps no server, so there is nothing to stand up first: the
      ;; database is a file under db/ that migrating creates.
      (println "Next Steps: ")
      (println "  Create your database with:  $ tram db:migrate")
      (println "  Start your server with:     $ tram dev"))))
