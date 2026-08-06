(ns ^:public tram.sse
  "Server sent events: a handler returns `:stream`, and this namespace turns the
  events it produces into a `text/event-stream` response.

  An event is a command map, per ADR-0010. `morph`, `remove-element`, and
  `render` build the ones Tram ships; an application writes the map itself for a
  command it registered. Each event is rendered as a partial response through the
  route's outgoing interceptors.

  `StreamSource` is extended here for a seq. The channel implementation lives in
  `tram.sse.async`, alongside `stream`, so this namespace does not require
  core.async."
  (:require [org.httpkit.server :as hk]
            [reitit.core :as r]
            [rhizome.html :as h]
            [tram.logging :as log]
            [tram.vars :refer [*current-user* *req* *res*]])
  (:import (java.io InputStream)
           (java.util.concurrent Executors TimeUnit)))

;;;; Events

(defn morph
  "An event that morphs `content`, hiccup or finished html, onto the page."
  [content]
  {:command     :dom/morph
   :dom/content content})

(defn remove-element
  "An event that removes the element with `id`."
  [id]
  {:command :dom/remove
   :dom/id  id})

(defn render
  "An event that morphs `template`, rendered with `locals`."
  [template locals]
  {:command  :dom/morph
   :template template
   :locals   locals})

(defn frame
  "One event as a `text/event-stream` frame.

  The event name is the command keyword with no leading colon. The data is the
  rest of the command map as edn on one line."
  [event]
  (str "event: "
       (subs (str (:command event)) 1)
       "\n"
       "data: "
       (pr-str (dissoc event :command))
       "\n\n"))

(defn comment-frame
  "A comment frame. The client discards it, which is what makes it a keep-alive."
  [text]
  (str ": " text "\n\n"))

;;;; Sources

(defprotocol StreamSource
  (drain! [source emit!]
    "Calls `emit!` with each event, in order, until the source is spent.
     Returns when it is. Blocking."))

(extend-protocol StreamSource
  clojure.lang.Seqable
  (drain! [events emit!]
    (doseq [evt (seq events)]
      (emit! evt))))

;;;; Rendering an event

(defn- leave-fns
  "The route's compiled interceptors as `:leave` fns, in the order a response
  goes out in.

  The compiled chain on `:result` is what actually ran, unlike the raw
  interceptors on `:data`, which reitit may have replaced."
  [req]
  (->> (get-in req [::r/match :result (:request-method req) :interceptors])
       (keep :leave)
       reverse))

(defn- event-response
  "An event as the response it is: a partial that arrives late."
  [event]
  (let [content (:dom/content event)]
    (cond-> {:status 200
             :locals (:locals event)}
      (string? content) (assoc :html content)
      (and content (not (string? content))) (assoc :hiccup content)
      (:template event) (assoc :template (:template event)))))

(defn- ->html
  "The rendered body as a string. Muuntaja's html encoder may have run, or may
  not have, so this takes either end of it."
  [body req]
  (cond
    (string? body) body
    (nil? body) ""
    (instance? InputStream body) (slurp body)
    :else
    (binding [*req* req]
      (str (h/html {:allow-raw true} body)))))

(defn event-renderer
  "A fn from an event to the same event with `:dom/content` as html.

  Built once per stream from the ctx that opened it, so an event costs the render
  pass and nothing else. Returns nil for an event that fails to render, which
  drops that event and leaves the stream open."
  [ctx]
  (let [req    (:request ctx)
        leaves (leave-fns req)
        base   (-> (select-keys ctx [:request :layouts])
                   (assoc ::event true))]
    (fn [event]
      (if-not (or (:dom/content event) (:template event))
        event
        (try
          (let [rendered (reduce (fn [c leave] (leave c))
                           (assoc base :response (event-response event))
                           leaves)]
            (assoc event
              :dom/content (->html (get-in rendered [:response :body]) req)))
          (catch Throwable t
            (log/event! ::event-render-failed
                        {:data {:event     event
                                :exception t}})
            nil))))))

;;;; The wire

(def ^:private stream-headers
  {"content-type"      "text/event-stream"
   "cache-control"     "no-cache"
   "x-accel-buffering" "no"})

(def ^:private keep-alive-ms
  25000)

(defprotocol EventEmitter
  (send-event! [emitter event]
    "Frames and writes one event. False once closed.")
  (send-comment! [emitter text]
    "Writes a comment frame. False once closed.")
  (close-stream! [emitter]
    "Ends the response."))

(defrecord HttpKitEmitter [channel]
  EventEmitter
  (send-event! [_ event] (hk/send! channel (frame event) false))
  (send-comment! [_ text] (hk/send! channel (comment-frame text) false))
  (close-stream! [_] (hk/close channel)))

(defn- start-keep-alive!
  "A comment frame every 25 seconds, so a proxy does not close an idle stream."
  [emitter]
  (doto (Executors/newSingleThreadScheduledExecutor)
    (.scheduleAtFixedRate #(send-comment! emitter "keep-alive")
                          keep-alive-ms
                          keep-alive-ms
                          TimeUnit/MILLISECONDS)))

(defn drain-stream!
  "Renders, frames, and writes every event `source` produces, then closes.

  Blocking. The transport runs it on a virtual thread; a test runs it against a
  recording emitter with no socket."
  [source emitter render-event]
  (try
    (drain! source
            (fn [event]
              (if-let [rendered (render-event event)]
                (send-event! emitter rendered)
                true)))
    (catch Throwable t
      (log/event! ::stream-failed {:data {:exception t}}))
    (finally (close-stream! emitter))))

(defn- open-stream! [ctx source]
  (let [req (:request ctx)
        render-event (event-renderer ctx)]
    (hk/as-channel req
                   {:on-open
                    (fn [channel]
                      ;; The head carries no body. An empty one writes the
                      ;; terminating chunk, and the client sees a finished
                      ;; response.
                      (hk/send! channel
                                {:status  200
                                 :headers stream-headers}
                                false)
                      (let [emitter    (->HttpKitEmitter channel)
                            keep-alive (start-keep-alive! emitter)]
                        (Thread/startVirtualThread
                          (fn []
                            (binding [*current-user* (:current-user req)
                                      *req*          req
                                      *res*          (:response ctx)]
                              (try
                                (drain-stream! source emitter render-event)
                                (finally (.shutdownNow keep-alive))))))))})))

(def stream-response-interceptor
  "Turns a response carrying `:stream` into a `text/event-stream` response.

  Sits outermost in the render group, so nothing downstream re-renders or
  encodes what it produced."
  {:name  :tram/stream-response
   :leave (fn [ctx]
            (if-let [source (get-in ctx [:response :stream])]
              (assoc ctx :response (open-stream! ctx source))
              ctx))})
