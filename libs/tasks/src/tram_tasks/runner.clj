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

(defn normalize-key [k]
  (get {"generate"  :generate
        "g"         :generate
        ;; things you can generate
        "migration" :migration
        "component" :component
        "comp"      :component
        ;; dev command
        "dev"       :dev}
       k
       k))

(m/defmethod run-task [:generate :component :default]
  [[_ _ component-name]]
  (let [project-name         (:project/name (tram/get-tram-config))
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
