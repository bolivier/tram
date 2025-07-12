(ns tram-tasks.runner
  "A lot of this code is heavily inspired by Biff.  Thanks to @jacobobryant"
  (:require [babashka.process :as p]
            [camel-snake-kebab.core :refer [->kebab-case ->snake_case] :as csk]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [methodical.core :as m]
            [migratus.core :as migratus]
            [nrepl.cmdline]
            [nrepl.core :as nrepl]
            [taoensso.telemere :as t]
            [tram.core :as tram]
            [tram.migrations :as tm]
            [zprint.core :refer [zprint-file-str]]))

(m/defmulti run-task
  identity)

(m/defmethod run-task [:db :migrate]
  [_]
  (tm/init)
  (tm/migrate))

(def command-specs
  {:generate {:aliases     ["g" "gen"]
              :desc        "Generate new code"
              :subcommands {:component {:aliases ["comp" "c"]
                                        :desc    "Generate a new component"
                                        :args    [{:name :component-name
                                                   :required true
                                                   :desc
                                                   "Name of the component"}]}
                            :migration {:aliases ["mig" "m"]
                                        :desc    "Generate a new migration"
                                        :args    [{:name :migration-name
                                                   :required true
                                                   :desc
                                                   "Name of the migration"}
                                                  {:name :fields
                                                   :required false
                                                   :variadic true
                                                   :desc
                                                   "Field definitions"}]}}}
   :dev      {:aliases []
              :desc    "Start development environment"
              :args    []}
   :db       {:aliases     []
              :desc        "Database operations"
              :subcommands {:migrate {:aliases []
                                      :desc    "Run database migrations"
                                      :args    []}}}})

(defn find-command-by-alias [command-map input]
  (->> command-map
       (filter (fn [[k v]]
                 (or (= (name k) input) (some #(= % input) (:aliases v)))))
       first
       first))

(defn parse-args [arg-specs input-args]
  (loop [specs  arg-specs
         inputs input-args
         result {}]
    (if (empty? specs)
      (if (empty? inputs)
        result
        (assoc result
          :extra-args inputs))
      (let [spec (first specs)
            {:keys [name required variadic]} spec]
        (cond
          variadic
          (assoc result
            name inputs)

          (empty? inputs)
          (if required
            (assoc result
              :error
              (str "Missing required argument: "
                   name))
            result)

          :else
          (recur (rest specs)
                 (rest inputs)
                 (assoc result
                   name
                   (first inputs))))))))

(def ^:dynamic *current-parsed-command*
  nil)

(m/defmethod run-task [:generate :component]
  [_]
  (let [component-name       (get-in *current-parsed-command*
                                     [:args :component-name])
        project-name         (:project/name (tram/get-tram-config))
        project-name-snake   (csk/->snake_case_string project-name)
        project-name-kebab   (csk/->kebab-case-string project-name)
        component-name-snake (csk/->snake_case_string component-name)
        component-name-kebab (csk/->kebab-case-string component-name)
        filename             (str "src/"
                                  project-name-snake
                                  "/components/"
                                  component-name-snake
                                  ".clj")
        file-contents        (str (list 'ns
                                        (symbol (str project-name-kebab
                                                     ".components."
                                                     component-name-kebab)))
                                  "\n\n"
                                  (list 'defn
                                        (symbol component-name-kebab)
                                        []
                                        nil))
        file-contents        (tram/format-source file-contents)]
    (io/make-parents filename)
    (spit filename file-contents)))

(m/defmethod run-task [:generate :migration]
  [_]
  (let [migration-name (get-in *current-parsed-command* [:args :migration-name])
        fields         (get-in *current-parsed-command* [:args :fields])]
    (println "doing migration" migration-name "with fields" fields)))

(defn generate-help
  ([]
   (generate-help command-specs ""))
  ([specs prefix]
   (str "Available commands:\n\n"
        (->> specs
             (map (fn [[cmd spec]]
                    (let [cmd-name (str prefix (name cmd))
                          aliases  (when (seq (:aliases spec))
                                     (str " (aliases: "
                                          (str/join ", "
                                                    (:aliases spec))
                                          ")"))
                          desc     (:desc spec)]
                      (str "  " cmd-name aliases "\n    " desc))))
             (str/join "\n\n")))))

(defn generate-subcommand-help [command]
  (when-let [subcommands (get-in command-specs [command :subcommands])]
    (str "Available subcommands for " (name command)
         ":\n\n" (->> subcommands
                      (map (fn [[subcmd spec]]
                             (let [subcmd-name (name subcmd)
                                   aliases     (when (seq (:aliases spec))
                                                 (str " (aliases: "
                                                      (str/join ", "
                                                                (:aliases spec))
                                                      ")"))
                                   desc        (:desc spec)]
                               (str "  " subcmd-name aliases "\n    " desc))))
                      (str/join "\n\n")))))

(m/defmethod run-task [:help]
  [_]
  (println (generate-help)))

(m/defmethod run-task :default
  [task]
  (println "Do not know how to run task" task)
  (println)
  (println (generate-help)))


(defn wait-for-nrepl
  "Waits for an nREPL server to be ready at the given host and port."
  [host port]
  (let [timeout-count 50
        interval-ms   100]
    (loop [timeout-count timeout-count]
      (if (zero? timeout-count)
        false
        (let [success? (try
                         (with-open [_sock (java.net.Socket. host
                                                             port)]
                           true)
                         (catch Exception _
                           false))]
          (if success?
            true
            (do (Thread/sleep interval-ms)
                (recur (dec timeout-count)))))))))


;; TODO configure this to run the integrant initialize server slash start
;; project stuff
(m/defmethod run-task [:dev]
  [_]
  (spit ".nrepl-port" 8777)
  (future
    (apply nrepl.cmdline/-main
      ["--port"
       8777
       "--middleware"
       "[cider.nrepl/cider-middleware,refactor-nrepl.middleware/wrap-refactor]"]))
  (wait-for-nrepl "localhost" 8777)
  (with-open [conn (nrepl/connect :port 8777)] ; adjust port
    (let [client (nrepl/client conn 1000)]
      (nrepl/message
        client
        {:op   "eval"
         :code "(ns user) (require '[integrant.repl :as ir]) (ir/reset)"}))))

(defn parse-command [args]
  (when (empty? args)
    (throw (ex-info "No command provided"
                    {:type :missing-command})))
  (let [command-str (first args)
        rest-args   (rest args)
        command     (find-command-by-alias command-specs command-str)]
    (when-not command
      (throw (ex-info (str "Unknown command: "
                           command-str)
                      {:type    :unknown-command
                       :command command-str})))
    (let [cmd-spec (get command-specs command)]
      (if (:subcommands cmd-spec)
        (do (when (empty? rest-args)
                  (throw (ex-info (str "Command "
                                       command-str
                                       " requires a subcommand")
                                  {:type    :missing-subcommand
                                   :command command})))
            (let [subcmd-str  (first rest-args)
                  subcmd-args (rest rest-args)
                  subcommand  (find-command-by-alias (:subcommands cmd-spec)
                                                     subcmd-str)]
              (when-not subcommand
                (throw (ex-info (str "Unknown subcommand: "
                                     subcmd-str)
                                {:type       :unknown-subcommand
                                 :command    command
                                 :subcommand subcmd-str})))
              (let [subcmd-spec (get-in cmd-spec
                                        [:subcommands subcommand])
                    parsed-args (parse-args (:args subcmd-spec)
                                            subcmd-args)]
                (when (:error parsed-args)
                  (throw (ex-info (:error parsed-args)
                                  {:type       :arg-parse-error
                                   :command    command
                                   :subcommand subcommand})))
                {:command      command
                 :subcommand   subcommand
                 :args         parsed-args
                 :dispatch-key [command subcommand]})))
        (let [parsed-args (parse-args (:args cmd-spec)
                                      rest-args)]
          (when (:error parsed-args)
            (throw (ex-info (:error parsed-args)
                            {:type    :arg-parse-error
                             :command command})))
          {:command      command
           :args         parsed-args
           :dispatch-key [command]})))))

(defn normalize-args [args]
  (try
    (let [parsed (parse-command args)]
      (:dispatch-key parsed))
    (catch Exception e
      (println "Error:" (.getMessage e))
      [:help])))

(defn -main [& args]
  (try
    (if (empty? args)
      (run-task [:help])
      (let [parsed (parse-command args)]
        (binding [*current-parsed-command* parsed]
          (run-task (:dispatch-key parsed)))))
    (catch Exception e
      (let [data (ex-data e)]
        (case (:type data)
          :missing-command    (do (println "Error: No command provided")
                                  (run-task [:help]))
          :unknown-command    (do (println "Error:"
                                           (.getMessage e))
                                  (run-task [:help]))
          :missing-subcommand (do (println "Error:"
                                           (.getMessage e))
                                  (println)
                                  (println (generate-subcommand-help (:command
                                                                       data))))
          :unknown-subcommand (do (println "Error:"
                                           (.getMessage e))
                                  (println)
                                  (println (generate-subcommand-help (:command
                                                                       data))))
          :arg-parse-error    (do (println "Error:"
                                           (.getMessage e))
                                  (when (:subcommand data)
                                        (println)
                                        (println (generate-subcommand-help
                                                   (:command data)))))
          (do (println "Unexpected error:"
                       (.getMessage e))
              (run-task [:help])))))))
