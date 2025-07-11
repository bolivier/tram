(ns tram-tasks.runner
  "A lot of this code is heavily inspired by Biff.  Thanks to @jacobobryant"
  (:require [babashka.process :as p]
            [clojure.string :as str]
            [methodical.core :as m]
            [migratus.core :as migratus]
            [nrepl.cmdline]
            [nrepl.core :as nrepl]
            [taoensso.telemere :as t]
            [tram.migrations :as tm]))

(m/defmulti run-task
  identity)

(m/defmethod run-task [:db :migrate]
  [_]
  (tm/init)
  (tm/migrate))

(defn normalize-key [k]
  (get {"generate"  :generate
        "g"         :generate
        "migration" :migration
        "dev"       :dev}
       k
       k))

(m/defmethod run-task :default
  [task]
  (println "Do not know how to run task" task))


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

(defn normalize-args [args]
  (mapv normalize-key args))

(defn -main [& args]
  (let [normalized-args (normalize-args args)]
    (run-task normalized-args)))
