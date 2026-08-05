(ns tram-docs.handlers.example-counter-handlers
  (:require [clojure.core.async :as async]
            [tram-docs.views.example-counter-views :as v]
            [tram.routes :as tr]
            [tram.sse :as sse]
            [tram.sse.async :as sse.async]))

(defonce global
  (atom 420))
(defonce user
  (atom 67))

(defn click-global [req]
  (swap! global inc)
  {:status 204
   :body   nil})

(defn click-user [req]
  (swap! user inc)
  (swap! global inc)
  {:status 204
   :body   nil})

(defn counter-example [req]
  {:status 200
   :locals {:user-count   @user
            :global-count @global}})

(defn- send-current!
  "The card rendered before its stream opened. Sending both buttons up front
  closes the window where another visitor's click landed in between."
  [ch]
  (and (async/>!! ch (sse/morph [v/global-button @global]))
       (async/>!! ch (sse/morph [v/user-button @user]))))

(defn- follow-counters!
  "Morphs a button every time its counter changes, until the client leaves.

  A watch runs on the thread that swapped the atom, so it puts on a sliding
  buffer rather than blocking a click. `>!!` returns false once the drain loop
  has closed `ch`, which is how a closed connection ends this loop."
  [ch]
  (let [changes (async/chan (async/sliding-buffer 8))
        follow! (fn [counter button]
                  (add-watch counter
                             changes
                             (fn [_ _ _ n] (async/put! changes [button n]))))]
    (follow! global v/global-button)
    (follow! user v/user-button)
    (try
      (loop []
        (when-let [content (async/<!! changes)]
          (when (async/>!! ch
                           (sse/morph content))
            (recur))))
      (finally (remove-watch global changes)
               (remove-watch user changes)
               (async/close! changes)))))

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
