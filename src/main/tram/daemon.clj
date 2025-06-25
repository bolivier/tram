(ns tram.daemon
  (:require [clojure.string :as str]
            [methodical.core :as m]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [nrepl.middleware :refer [set-descriptor!]]
            [nrepl.misc :refer [response-for]]
            [nrepl.server :as nrepl]
            [nrepl.transport :as transport]
            [taoensso.telemere :as t]
            [toucan2.core :as t2]
            [tram.core :as tram]
            [tram.generators.model :as gen.model]
            [zprint.core :refer [zprint-file-str]]))

(t/remove-handler! :default/console)
(t/add-handler! :default/file (t/handler:file {:path "tram-daemon.log"}))

(defn get-from-project
  "Reaches into the namespace `ns`, which exists in the user application (ie. not
  a tram namespace), and grabs the var `name`, then fetches the value of that
  var and returns it as data or a callable fn. "
  [ns name]
  (require ns)
  (when-let [ns-var (resolve (symbol (str ns) (str name)))]
    (deref ns-var)))

(defn seed-database [migration-config]
  (let [up (get-from-project 'seeds.init 'up)]
    (if (fn? up)
      (t2/with-connection [_ (:db migration-config)]
        (t/log! {:level :info
                 :id    :db/seeding
                 :data  {:config migration-config}})
        (up))
      (throw
        (ex-info
          "Could not find seed function.  Please create seeds.init/up and try again."
          {})))))

(defn migrate-database [migration-config]
  (t/log! {:level :info
           :id    :db/migrating
           :data  {:config migration-config}})
  (migratus/migrate migration-config))

(defn database-exists? [db-name]
  (try
    (boolean (not-empty (jdbc/execute!
                          (tram/get-database-config)
                          ["SELECT 1 FROM pg_database WHERE datname = ?"
                           db-name])))
    (catch Exception _
      false)))

(defn create-database [db-name]
  (when-not (database-exists? db-name)
    (t/log! {:level :info
             :id    :db/creating
             :data  {:db-name db-name}})
    ;; Cannot connect to db config for nonexistent db!
    (try
      (jdbc/execute! (assoc (tram/get-tram-database-config)
                       :dbname "postgres")
                     [(str "CREATE DATABASE "
                           db-name)])
      (catch Exception e
        (t/log! {:level :error
                 :id    :error/failed-to-drop-db
                 :data  {:err e}})
        (throw e)))))

(defn drop-database [db-name]
  (t/log! {:level :info
           :id    :db/dropping
           :data  {:db-name db-name}})
  (try
    (jdbc/execute! (tram/get-tram-database-config)
                   [(str "DROP DATABASE IF EXISTS " db-name)])
    (catch Exception e
      (t/log! {:level :error
               :id    :error/failed-to-drop-db
               :data  {:err e}})
      (throw (ex-info (.getMessage e) {})))))

(defn database-precheck!
  "Checks on database configuration.

  Creates database if not exists."
  [db-name]
  (when-not (database-exists? db-name)
    (create-database db-name)))

(m/defmulti handle-cmd
  "Handle a cmd from the tram cli client.

   The format for these commands is <category>:<optional-env>:<task>.

   There's no hard requirement for them to only be 3, but the category and optional
   env in the second position are baked in to the implementation."
  (fn [{:keys [split-cmd]}]
    (t/log! {:id   :handle-cmd/dispatcher
             :data [split-cmd]})
    split-cmd))

(m/defmethod handle-cmd :default
  [{:keys [cmd]
    :as   msg}]
  (response-for
    msg
    {:status #{"error" "done"}
     :stdout
     (str
       "Unknown command: "
       cmd
       "

Supported commands:
db:env:migrate
db:env:undo

config:generate")}))

(m/defmethod handle-cmd ["config" "generate"]
  [msg]
  (t/log! {:level :info
           :data  {:msg (assoc msg :transport :omitted)}})
  (response-for msg
                {:status #{"done"}
                 :stdout (zprint-file-str (str (tram/generate-config
                                                 (first (:args msg))))
                                          ::config
                                          (tram/get-zprint-config))}))

(m/defmethod handle-cmd ["generate" "model"]
  [msg]
  (def msg
    msg)
  (let [blueprint (gen.model/parse-blueprint (:args msg))]
    (gen.model/generate blueprint)
    (response-for msg
                  {:result "success"
                   :status #{"done"}})))

(defn msg->env [{:keys [split-cmd]}]
  (if (and (some? split-cmd)
           (<= 3
               (count split-cmd)))
    (second split-cmd)
    (tram/get-env)))

(m/defmethod handle-cmd :before
  ["db" :default :default]
  [msg]
  (database-precheck! (tram/get-database-name (msg->env msg)))
  msg)

(m/defmethod handle-cmd ["db" :default "drop"]
  [msg]
  (let [[_ env] (:split-cmd msg)
        db-name (tram/get-database-name env)]
    (drop-database db-name)
    (response-for msg
                  {:result "success"
                   :status #{"done"}})))

(m/defmethod handle-cmd ["db" :default "reset"]
  [msg]
  (let [[_ env] (:split-cmd msg)
        db-name (tram/get-database-name env)]
    (drop-database db-name)
    (create-database db-name)
    (migrate-database (tram/get-migration-config env))
    (seed-database (tram/get-migration-config env))
    (response-for msg
                  {:result "success"
                   :status #{"done"}})))

(m/defmethod handle-cmd ["db" :default "migrate"]
  [msg]
  (let [[_ env] (:split-cmd msg)]
    (migrate-database (tram/get-migration-config env))
    (response-for msg
                  {:result "success"
                   :status #{"done"}})))

(m/defmethod handle-cmd ["db" :default "seed"]
  [msg]
  (let [[_ env] (:split-cmd msg)]
    (seed-database (tram/get-migration-config env))
    (response-for msg
                  {:result "success"
                   :status #{"done"}})))

(m/defmethod handle-cmd ["db" :default "undo"]
  [msg]
  (let [[_ env] (:split-cmd msg)]
    (migratus/rollback (tram/get-migration-config env))
    (response-for msg
                  {:result "success"
                   :status #{"done"}})))

(m/defmethod handle-cmd ["db" :default "create"]
  [msg]
  (let [[_ env]        (:split-cmd msg)
        migration-name (first (:args msg))
        files          (migratus/create (tram/get-migration-config env)
                                        migration-name)]
    (response-for msg
                  {:result (str "created migration " migration-name)
                   :stdout (str "Created " (str/join ", " files))
                   :status #{"done"}})))

(defn get-split-cmd
  "Split cmd into pieces separated by a :.

  Sometimes there is an optional env in the middle, and this function ensures
  that optional env is present.

  Eventually I'll want a more robust data driven solution for this."
  [cmd]
  (t/log! (str "splitting " cmd))
  (let [split' (str/split cmd #":")]
    (cond
      (and (= "db" (first split')) (= 2 (count split')))
      [(first split') (tram/get-env) (last split')]

      :else split')))

(defn wrap-tram
  "nREPL middleware to handle the \"tram/op\" operation."
  [handler]
  (fn [{:keys [op transport cmd]
        :as   msg}]
    (if (= op "tram/op")
      (let [out (java.io.StringWriter.)
            err (java.io.StringWriter.)]
        (binding [*out* out
                  *err* err]
          (let [result (try
                         (handle-cmd (assoc msg
                                       :split-cmd (get-split-cmd cmd)))
                         (catch Exception e
                           (response-for msg
                                         {:status  #{"exception" "done"}
                                          :message (.getMessage e)
                                          :stdout  (str out)
                                          :stderr  (.getMessage e)})))]
            (transport/send transport
                            (merge {:stdout (str out)
                                    :stderr (str err)}
                                   result)))))
      (handler msg))))

;; Required registration for nrepl middleware.
(set-descriptor!
  #'wrap-tram
  {:requires #{"clone"}
   :handles
   {"tram/op"
    {:doc "Perform a Tram operation identified by :cmd (e.g. \"db.seed\")."
     :requires {"cmd" "String name of the sub-command to execute."}
     :optional
     {"args"
      "EDN map of arguments for the command (if required by the sub-command)."}

     :returns
     {"result" "Result of the operation (present on success)."
      "status"
      "A vector of status flags: includes \"done\" on success or \"error\" on failure."

      "message" "Error message (present if status is \"error\")."}}}})

(defonce server
  (atom nil))

(defn start! []
  (t/log! {:level :info
           :id    :starting
           :data  {}})
  (reset! server (nrepl/start-server :port    7888 ;; or 0 for random
                                     :handler (nrepl/default-handler
                                                #'wrap-tram))))

(defn stop! []
  (when @server
    (.close @server)
    (reset! server nil)
    (println "nREPL stopped")));1

(defn start-tram! []
  (try
    (start!)
    @(promise)
    (finally (clojure.java.io/delete-file "/tmp/tram-daemon.pid"))))

(defn -main [& args]
  (future (start-tram!)))

(comment
  (-main)
  (slurp "./tram.edn")
  (stop!))
