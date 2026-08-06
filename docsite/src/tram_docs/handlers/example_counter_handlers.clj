(ns tram-docs.handlers.example-counter-handlers
  (:require [clojure.core.async :as async]
            [tram-docs.views.example-counter-views :as v]
            [tram.routes :as tr]
            [tram.sse :as sse]
            [tram.sse.async :as sse.async]))
(defonce state
  (atom {:global-count 420
         :user-count   67}))

(defn click-global [req]
  (swap! state update
    :global-count
    inc)
  {:status 204
   :body   nil})

(defn click-user [req]
  (swap! state (fn [state]
                 (-> state
                     (update :user-count inc)
                     (update :global-count inc))))
  {:status 204
   :body   nil})

(defn counter-example [req]
  {:status 200
   :locals @state})

(defn- send-current!
  "The card rendered before its stream opened. Sending both buttons up front
  closes the window where another visitor's click landed in between."
  [ch]
  (and (async/>!! ch (sse/morph [v/global-button (:global-count @state)]))
       (async/>!! ch (sse/morph [v/user-button (:user-count @state)]))))

(defn- follow-counters!
  "Morphs a button every time its counter changes, until the client leaves.

  A watch runs on the thread that swapped the atom, so it puts on a sliding
  buffer rather than blocking a click. `>!!` returns false once the drain loop
  has closed `ch`, which is how a closed connection ends this loop."
  [ch]
  (let [changes (async/chan (async/sliding-buffer 8))]
    (add-watch state
               changes
               (fn [_ _ _ n]
                 (async/put! changes [v/global-button (:global-count n)])
                 (async/put! changes [v/user-button (:user-count n)])))
    (try
      (loop []
        (when-let [content (async/<!! changes)]
          (when (async/>!! ch
                           (sse/morph content))
            (recur))))
      (finally (remove-watch state changes) (async/close! changes)))))

(defn counter-stream [req]
  {:status 200
   :stream (sse.async/stream req
                             (fn [ch]
                               (when (send-current! ch)
                                 (follow-counters! ch))))})

(tr/defroutes routes
  ["/counter"
   [""
    {:name :route/examples.counter
     :get  counter-example}]
   ["/stream"
    {:name :route/examples.counter.stream
     :get  counter-stream}]
   ["/global"
    {:name :route/examples.counter.global
     :post click-global}]
   ["/user"
    {:name :route/examples.counter.user
     :post click-user}]])
