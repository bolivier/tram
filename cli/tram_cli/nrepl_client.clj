(ns tram-cli.nrepl-client
  (:require [bencode.core :as b]
            [tram-cli.daemon-management :as dm]))

(defn bytes->str [x]
  (if (bytes? x)
    (String. (bytes x))
    (str x)))

(defn read-msg [msg]
  (let [res (zipmap (map keyword (keys msg))
                    (map #(if (bytes? %)
                            (String. (bytes %))
                            %)
                      (vals msg)))
        res (if-let [status (:status res)]
              (assoc res :status (mapv bytes->str status))
              res)
        res (if-let [status (:sessions res)]
              (assoc res :sessions (mapv bytes->str status))
              res)]
    res))

(defn read-reply [in session id]
  (loop []
    (let [msg (read-msg (b/read-bencode in))]
      (if (and (= (:session msg) session)
               (= (:id msg) id))
        msg
        (recur)))))

(defn coerce-long [x]
  (if (string? x)
    (Long/parseLong x)
    x))

(def current-id (atom 0))

(defn next-id []
  (str (swap! current-id inc)))

(defn raw-send [msg]
  (try
    (let [s   (java.net.Socket. "localhost" (coerce-long dm/default-port))
          out (.getOutputStream s)
          in  (java.io.PushbackInputStream. (.getInputStream s))
          id  (next-id)
          _ (b/write-bencode out
                             {"op" "clone"
                              "id" id})
          {session :new-session} (read-msg (b/read-bencode in))
          id  (next-id)
          _ (b/write-bencode out
                             (assoc msg
                               :id      id
                               :session session))]
      (loop [m {:vals      []
                :responses []}]
        (let [{:keys [status stdout value stderr]
               :as   resp}
              (read-reply in session id)]
          (when stdout
            (print stdout)
            (flush))
          (when stderr
            (binding [*out* *err*]
              (println stderr)))
          (when (= "true" (System/getenv "TRAM_DEBUG"))
            (prn resp))
          (let [m (cond-> (update m :responses conj resp)
                    value (update :vals conj value))]
            (when-not (some #{"done"}
                            status)
              (recur m))))))
    (catch java.net.ConnectException _
      (println "The tram server isn't running."))))

;2
(defn send [msg]
  (dm/ensure-daemon-is-running!)
  (raw-send msg))
