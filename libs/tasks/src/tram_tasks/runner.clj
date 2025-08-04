(ns tram-tasks.runner
  "A lot of this code is heavily inspired by Biff.  Thanks to @jacobobryant"
  (:require [clojure.string :as str]
            [methodical.core :as m]
            [tram.migrations :as tm]))

(m/defmulti run-task
  :dispatch-key)

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
   :new      {:desc "Create a new template for a tram project"
              :args [{:name     :project-name
                      :required true
                      :desc     "Name of the project to create"}]}
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

(m/defmethod run-task [:generate :migration]
  [cmd]
  (let [migration-name (get-in cmd [:args :migration-name])
        fields         (get-in cmd [:args :fields])]
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





(defn parse-command [args]
  (when (empty? args)
    (throw (ex-info "No command provided"
                    {::type :missing-command})))
  (let [command-str (first args)
        rest-args   (rest args)
        command     (find-command-by-alias command-specs command-str)]
    (when-not command
      (throw (ex-info (str "Unknown command: "
                           command-str)
                      {::type   :unknown-command
                       :command command-str})))
    (let [cmd-spec (get command-specs command)]
      (if (:subcommands cmd-spec)
        (do (when (empty? rest-args)
                  (throw (ex-info (str "Command "
                                       command-str
                                       " requires a subcommand")
                                  {::type      :missing-subcommand
                                   :command    command
                                   :subcommand nil})))
            (let [subcmd-str  (first rest-args)
                  subcmd-args (rest rest-args)
                  subcommand  (find-command-by-alias (:subcommands cmd-spec)
                                                     subcmd-str)]
              (when-not subcommand
                (throw (ex-info (str "Unknown subcommand: "
                                     subcmd-str)
                                {::type      :unknown-subcommand
                                 :command    command
                                 :subcommand subcmd-str})))
              (let [subcmd-spec (get-in cmd-spec
                                        [:subcommands subcommand])
                    parsed-args (parse-args (:args subcmd-spec)
                                            subcmd-args)]
                (when (:error parsed-args)
                  (throw (ex-info (:error parsed-args)
                                  {::type      :arg-parse-error
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
                            {::type   :arg-parse-error
                             :command command})))
          {:command      command
           :args         parsed-args
           :dispatch-key [command]})))))

(defn -main [& args]
  (try
    (run-task (parse-command args))
    (catch Exception e
      (let [data (ex-data e)
            expected-error? (contains? data ::type)
            msg  (if expected-error?
                   (str "Error: "
                        (.getMessage e))
                   (str "Unexpected error: "
                        (.getMessage e)))]
        (println msg)
        (if (contains? data
                       :subcommand)
          (do (println)
              (println (generate-subcommand-help (:command data))))
          (run-task [:help]))))))
