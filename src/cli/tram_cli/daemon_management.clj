(ns tram-cli.daemon-management
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.string :as str]))

(def default-port
  7888)
(def daemon-pid-file
  "/tmp/tram-daemon.pid")

(defn kill-daemon []
  (when (fs/exists? daemon-pid-file)
    (try
      (let [pid (str/trim (slurp "/tmp/tram-daemon.pid"))]
        (when pid
          (p/sh (str "sudo kill "
                     pid))))
      (finally (fs/delete-if-exists "/tmp/tram-daemon.pid")))))

(defn accepting? []
  (try
    (with-open [_ (java.net.Socket. "127.0.0.1" default-port)]
      true)
    (catch Exception _
      false)))

(defn start-daemon []
  (when-not (or (accepting?)
                (fs/exists? daemon-pid-file))
    (let [proc (p/process {:out "tram-daemon.log"
                           :err "tram-daemon.log"}
                          "clj" "-M:dev"
                          "-m"  "tram.daemon")]
      (when-let [pid (some-> proc
                             :proc
                             (.pid))]
        (spit daemon-pid-file
              pid)))))

(defn is-dead? []
  (and (not (accepting?)) (not (fs/exists? daemon-pid-file))))

(defn ensure-daemon-is-running! []
  (when-not (accepting?)
    (binding [*out* *err*] ;; ensures stdout redirect works fine for config
                           ;; output
      (println "Starting Tram daemon, please wait...")
      (start-daemon)
      ;; 20 seconds -- 200 instances of waiting 100 ms
      ;; = 200 * 100ms = 20,000ms = 20s
      (loop [times-left 200]
        (cond
          (accepting?) nil
          (zero? times-left)
          (do (println "Server took too long to start.")
              (kill-daemon))

          :else
          (do (Thread/sleep 100)
              (recur (dec times-left))))))))

(defn restart-daemon []
  (println "Killing daemon")
  (kill-daemon)
  (loop [times 20]
    (if (zero? times)
      (println "Timed out trying to kill daemon, please handle manually.")
      (when-not (is-dead?)
        (Thread/sleep 250)
        (recur (dec times)))))
  (start-daemon)
  (ensure-daemon-is-running!))
