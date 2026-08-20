(ns rhizome.sse
  "Reading a `text/event-stream` response.

  A frame is a command: its `event` field names the operation and its `data`
  field carries the rest as edn. So a stream adds no vocabulary, and a server can
  fire anything the page already knows how to do.

  Spec: `docs/specs/rhizome/03-server-sent-events.md`."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defn split-frames
  "The complete frames in `buffer`, and what follows the last one.

  Chunk boundaries and frame boundaries have nothing to do with each other. One
  chunk may hold six frames or a third of one."
  [buffer]
  (let [parts (str/split buffer #"\r?\n\r?\n" -1)]
    [(butlast parts) (last parts)]))

(defn- field
  "A frame line as its field name and value. One optional space after the colon
  belongs to the format, not to the value."
  [line]
  (let [colon (.indexOf line ":")]
    (if (neg? colon)
      [line ""]
      (let [value (subs line
                        (inc colon))]
        [(subs line
               0
               colon)
         (if (str/starts-with? value
                               " ")
           (subs value
                 1)
           value)]))))

(defn parse-frame
  "A frame's event name and its data lines.

  A line with no field name is a comment, which is what a keep-alive is. `id` and
  `retry` are read and dropped, because nothing reconnects."
  [text]
  (reduce (fn [frame line]
            (let [[name value] (field line)]
              (case name
                "event" (assoc frame :event value)
                "data"  (update frame :data (fnil conj []) value)
                frame)))
    {}
    (remove str/blank? (str/split-lines text))))

(defn frame->command
  "The command a frame invokes, or nil when it carries none.

  A frame with no data dispatches nothing, which is what makes a keep-alive
  free."
  [{:keys [data event]}]
  (when (and event
             (seq data))
    (assoc (edn/read-string (str/join "\n"
                                      data))
      :do (keyword event))))

(defn- run-frame! [text execute]
  (try
    (when-let [command (frame->command (parse-frame text))]
      (execute command))
    (catch js/Error e
      ;; One bad frame is not a broken connection.
      (js/console.warn "rhizome: dropped a stream frame" text e))))

(defn consume!
  "Reads `response` to its end, running `execute` on each frame's command.

  Returns a promise that resolves when the stream closes."
  [response execute]
  (let [reader  (.getReader (.-body response))
        decoder (js/TextDecoder. "utf-8")]
    (letfn [(pump [buffer]
              (.then (.read reader)
                     (fn [result]
                       (when-not (.-done result)
                         (let [text (str buffer
                                         (.decode decoder
                                                  (.-value result)
                                                  #js {:stream true}))
                               [frames leftover] (split-frames text)]
                           (doseq [frame frames]
                             (run-frame! frame
                                         execute))
                           (pump leftover))))))]
      (pump ""))))
