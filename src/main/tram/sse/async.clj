(ns ^:public tram.sse.async
  "The core.async half of server sent events: a channel as a `StreamSource`, and
  `stream`, which owns the thread that fills one.

  Separate from `tram.sse` so a stream of known events needs no core.async."
  (:require [clojure.core.async :as async]
            [tram.logging :as log]
            [tram.sse :as sse]
            [tram.vars :refer [*current-user* *req*]])
  (:import (clojure.core.async.impl.channels ManyToManyChannel)))

(extend-protocol sse/StreamSource
  ManyToManyChannel
  (drain! [ch emit!]
    (loop []
      (when-let [event (async/<!! ch)]
        (if (emit! event)
          (recur)
          ;; The client is gone. Closing makes the producer's `>!!` return
          ;; false, which ends an ordinary `while` loop.
          (async/close! ch))))))

(defn stream
  "Runs `produce!` on its own thread and returns the channel it fills.

  A handler cannot fill its own channel in place: `>!!` blocks once the buffer is
  full, and nothing drains the channel until the handler has returned.

  Never produce events in a `go` block. Blocking i/o there starves the pool."
  ([req produce!]
   (stream req {} produce!))
  ([req
    {:keys [buffer]
     :or   {buffer 16}}
    produce!]
   (let [ch   (async/chan buffer)
         user (:current-user req)]
     (async/thread (binding [*current-user* user
                             *req* req]
                     (try
                       (produce! ch)
                       (catch Throwable t
                         (log/event! ::producer-failed {:data {:exception t}}))
                       (finally (async/close! ch)))))
     ch)))
