(ns tram-cli.entry
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
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
(def cmd-spec
  {:spec     {:help {:coerce :boolean
                     :alias  :h
                     :desc   "Print this help menu."}}
   :restrict true})

(defn do-show-help [_]
  (println
    (str/trim
      "
tram <command>

Usage:

tram new <name>         create a new project in this directory
tram test               run unit tests (--watch to watch)
tram hiccup             convert clipboard contents from html to hiccup (alias html)
tram dev                run dev commands (tasks)
tram generate           tram generators subcommand (run with -h to see more)
tram help               print this menu
")))

(defn do-new-project [{:keys [opts]}]
  (let [{:keys [new-project-name]} opts]
    (render-new-project-template new-project-name)))

(defn do-generate-here [{:keys [args]}]
  (prn "generating")
  (do-generate args))

(defn do-test [{:keys [opts]}]
  (let [watch     (:watch opts)
        test-type (if (fs/exists? (io/resource "bin/test"))
                    :kaocha
                    :clojure)
        cmd       (case test-type
                    :kaocha  "bin/kaocha"
                    :clojure "clojure -X:test")
        watch-cmd (case test-type
                    :kaocha  " --watch"
                    :clojure ":watch")
        cmd       (if watch
                    (str cmd
                         watch-cmd)
                    cmd)]
    (if watch
      (println "Watching tests...")
      (println "Running tests..."))
    (p/shell [cmd])))

(defn do-html-conversion [{:keys [args]}]
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

(defn task-file->ns
  "Convert an io/file into a namespace that is compatible with bb -m."
  [file]
  (let [rev-path  (reverse (str/split (str file) #"/"))
        filename  (first rev-path)
        ns-suffix (-> filename
                      (str/replace #"\.clj$" "")
                      (str/replace "_" "-"))]
    (loop [rev-path (rest rev-path)
           path     (list ns-suffix)]
      (cond
        (or (empty? rev-path) (= "tasks" (first rev-path))) (str/join "." path)
        (empty? (first rev-path)) (recur (rest rev-path) (first rev-path))
        :else (recur (rest rev-path) (conj path (first rev-path)))))))

(defn do-dev [_]
  (println "Starting development environment...")
  (let [task-files (map task-file->ns
                     (fs/list-dir (io/file user-project-dir "tasks" "dev")))
        processes  (mapv (fn [task]
                           (p/process {:dir user-project-dir
                                       :err :inherit
                                       :out :inherit}
                                      (str "bb -cp 'tasks' -m " task)))
                     task-files)]
    (doseq [process processes]
      (deref process))))

(defn do-db-migrate [_]
  (let [p (p/process {:out :inherit
                      :err :inherit}
                     "clojure -X tram.db/migrate-from-cli")]
    (println "Migrating database.")
    (println "Starting JVM...")
    (println
      "Did you know you can run migrations from the dev/migrations.clj namespace?")
    @p))

(def cmd-table
  [{:cmds       ["new"]
    :fn         do-new-project
    :args->opts [:new-project-name]}
   {:cmds ["g"]
    :fn   do-generate-here}
   {:cmds ["generate"]
    :fn   do-generate-here}
   {:cmds ["test"]
    :fn   do-test}
   {:cmds ["help"]
    :fn   do-show-help}
   {:cmds ["hiccup"]
    :fn   do-html-conversion}
   {:cmds ["html"]
    :fn   do-html-conversion}
   {:cmds ["db:migrate"]
    :fn   do-db-migrate}
   {:cmds ["dev"]
    :fn   do-dev}
   {:cmds []
    :fn   (fn [{:keys [args]}]
            (if (empty? args)
              (do-show-help args)
              (p/shell (str "clojure -M:tram "
                            (str/join " "
                                      args)))))}])

(defn -main [& cli-args]
  (cli/dispatch cmd-table cli-args {:aliases {:g :generate}}))
