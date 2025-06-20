(ns tram.daemon
  (:require [cider.nrepl :refer [cider-middleware]]
            [clojure.string :as str]
            [methodical.core :as m]
            [migratus.core :as migratus]
            [nrepl.middleware :refer [set-descriptor!]]
            [nrepl.misc :refer [response-for]]
            [nrepl.server :as nrepl]
            [nrepl.transport :as transport]
            [toucan2.core :as t2]
            [tram.core :as tram]))

(defn get-from-project [ns name]
  (require ns)
  (deref (resolve (symbol (str ns) (str name)))))

(defn seed-database [msg env]
  (let [up (get-from-project 'seeds.init 'up)]
    (t2/with-connection [_ (tram/get-database-config env)]
      (up))))

(defn migrate-database [msg env]
  (migratus/migrate (tram/get-migration-config env)))

(m/defmulti handle-cmd :split-cmd)

(m/defmethod handle-cmd :default
  [{:keys [cmd]
    :as   msg}]
  (response-for msg
                {:status  #{"error" "done"}
                 :message (str "Unknown command: " cmd)}))

(m/defmethod handle-cmd ["db" :default "migrate"]
  [msg]
  (let [[_ env] (:split-cmd msg)]
    (migrate-database msg env)
    (response-for msg
                  {:result "success"
                   :status #{"done"}})))

(m/defmethod handle-cmd ["db" :default "seed"]
  [msg]
  (let [[_ env] (:split-cmd msg)]
    (seed-database msg env)
    (response-for msg
                  {:result "success"
                   :status #{"done"}})))

(defn get-split-cmd
  "Split cmd into pieces separated by a :.

  Sometimes there is an optional env in the middle, and this function ensures
  that optional env is present.

  Eventually I'll want a more robust data driven solution for this."
  [cmd]
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
      ;; For other ops, delegate to the next handler in the chain:
      (handler msg))))

;; Register middleware descriptor for tooling (e.g. nREPL "describe"):
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

(defonce server (atom nil))

(defn start! []
  (reset! server (nrepl/start-server :port    7888 ;; or 0 for random
                                     :handler (nrepl/default-handler
                                                #'wrap-tram))))

(defn stop! []
  (when @server
    (.close @server)
    (reset! server nil)
    (println "nREPL stopped")));1

(defn start-tram! []
  (start!)
  @(promise))

(defn -main [& args]
  (future (start-tram!)))


(comment
  (-main)
  (slurp "./tram.edn")
  (stop!))
