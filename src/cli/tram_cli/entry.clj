(ns tram-cli.entry
  "Entrypoint for the tram cli.

  Builtin commands:
  `new` - make a new project (copies template)
  `hiccup` - convert html to hiccup (reads from clipboard)
  `html` - alias for `hiccup`
  `dev` - Run the commands in ./tasks/dev
  `test` Run tests, supports using bin/test or clj -X:test. Supports watch with --watch.
         Watch either appends `:watch` alias, or passes flag to bin/test."
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :refer [prewalk]]
            [hickory.core :as hc]
            [rhizome.html :as html]
            [tram-cli.generator.new :refer [render-new-project-template]]
            [tram.tram-config :refer [get-tram-config]]))

(def user-project-dir
  (io/file (System/getenv "TRAM_CLI_CALLED_FROM")))

(alter-var-root #'p/*defaults*
                #(assoc %
                   :continue true
                   :dir      user-project-dir))

(defn do-show-help [_]
  (println
    (str/trim
      "
tram <command>

Usage:

tram start              start the application
tram new <name>         create a new project in this directory
tram run <name>         runs an executable from bin
tram dev                alias for `tram run dev`
tram test               alias for `tram run test`
tram hiccup             convert clipboard contents from html to hiccup (alias html)
tram html               alias for `tram hiccup`

tram help               print this menu
")))

(defn do-new-project [{:keys [opts]}]
  (let [{:keys [new-project-name]} opts]
    (render-new-project-template new-project-name)))



(defn empty-coll? [x]
  (and (coll? x) (empty? x)))

(defn hiccup-has-prop? [node prop]
  (and (vector? node) (map? (second node)) (some? (get-in node [1 prop]))))

(defn convert-id-to-hash-notation [node]
  (if (hiccup-has-prop? node
                        :id)
    (let [tag      (first node)
          props    (second node)
          new-tag  (keyword (str (name tag)
                                 "#"
                                 (:id props)))
          children (drop 2
                         node)]
      (into [new-tag
             (dissoc props
               :id)]
            children))
    node))

(defn keyword-safe-class?
  "Tailwind classes may contain characters, like `/` and `:`, that a keyword
  cannot hold. Those have to stay in the `:class` prop."
  [class]
  (re-matches #"[A-Za-z0-9_-]+" class))

(defn convert-classes-to-dot-notation [node]
  (if (hiccup-has-prop? node
                        :class)
    (let [tag (first node)
          props (second node)
          classes (remove str/blank?
                    (str/split (:class props)
                               #"\s+"))
          {safe   true
           unsafe false}
          (group-by (comp boolean
                          keyword-safe-class?)
                    classes)

          new-tag (if (seq safe)
                    (keyword (str (name tag)
                                  "."
                                  (str/join "."
                                            safe)))
                    tag)
          new-props (if (seq unsafe)
                      (assoc props
                        :class (str/join " "
                                         unsafe))
                      (dissoc props
                        :class))
          children (drop 2
                         node)]
      (into [new-tag new-props]
            children))
    node))

(defn remove-empty-from-hiccup [node]
  (if (vector? node)
    (into []
          (remove empty-coll?
            node))
    node))

(defn get-clipboard-contents []
  (str/trim (:out (p/shell {:out :string}
                           (if (= "Darwin\n"
                                  (:out (p/shell {:out :string} "uname")))
                             "pbpaste"
                             "wl-paste")))))

(defn do-convert-html-to-hiccup [{:keys [args]}]
  (let [html (or (first args)
                 (str/trim (:out (p/shell {:out :string}
                                          (get-clipboard-contents)))))
        remove-html-whitespace #(str/replace % #">\s*[\r\n]+\s*<" "><")]
    (try
      (->> html
           str/trim
           remove-html-whitespace
           hc/parse-fragment
           first
           hc/as-hiccup
           (prewalk (comp remove-empty-from-hiccup
                          convert-classes-to-dot-notation
                          convert-id-to-hash-notation))
           prn)
      (catch Exception e
        (println "Could not convert into hiccup: ")
        (prn e)
        (prn html)))))

(defn do-start [_]
  (p/shell (format "clojure -M:tram -m %s.core"
                   (name (:project/name (get-tram-config user-project-dir))))))

(defn run [fd]
  (cond
    (not (fs/exists? fd)) (println "File" (fs/absolutize fd) "does not exist")
    (fs/directory? fd)
    (let [ps (mapv (comp run str) (fs/list-dir fd))]
      (doseq [p ps]
        @p))

    :else
    (p/process {:dir user-project-dir
                :err :inherit
                :out :inherit}
               fd)))

(defn do-run [{:keys [args]}]
  (run (io/file (str user-project-dir "/bin/" (first args)))))

(defn do-dev [_]
  (println "Starting development environment...")
  (run (io/file (str user-project-dir "/bin/dev"))))

(defn do-test [_]
  (run (io/file (str user-project-dir "/bin/test"))))

(def cmd-table
  [{:cmds       ["new"]
    :fn         do-new-project
    :args->opts [:new-project-name]}
   {:cmds ["test"]
    :fn   do-test}
   {:cmds ["help"]
    :fn   do-show-help}
   {:cmds ["hiccup"]
    :fn   do-convert-html-to-hiccup}
   {:cmds ["html"]
    :fn   do-convert-html-to-hiccup}
   {:cmds ["dev"]
    :fn   do-dev}
   {:cmds ["start"]
    :fn   do-start}
   {:cmds ["run"]
    :fn   do-run}
   {:cmds []
    :fn   (fn [{:keys [args]}]
            (if (empty? args)
              (do-show-help args)
              (p/shell (str "clojure -M:tram "
                            (str/join " "
                                      args)))))}])

(defn -main [& cli-args]
  (cli/dispatch cmd-table cli-args {}))
