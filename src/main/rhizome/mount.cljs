(ns rhizome.mount
  "The scan, the mutation observer, and listener lifecycle."
  (:require [clojure.edn :as edn]
            [rhizome.dom :as dom]
            [rhizome.triggers :as triggers]
            [rhizome.utils :refer [kw->string]]))

(defn- wired [el]
  (or (unchecked-get el "rhizomeWired") #{}))

(defn- mark-wired! [el trigger]
  (unchecked-set el "rhizomeWired" (conj (wired el) trigger)))

(defn- read-directive [el trigger]
  (try
    (edn/read-string (get el trigger))
    (catch js/Error _
      (js/console.warn "rhizome: could not parse edn in"
                       (kw->string trigger)
                       el)
      nil)))

(defn- debouncer
  "A defer function that runs `f` once the calls stop for `ms`.

  One timer per wired element, so elements debounce independently."
  []
  (let [timer (atom nil)]
    (fn [ms f]
      (some-> @timer
              js/clearTimeout)
      (reset! timer (js/setTimeout f ms)))))

(defn- wire! [el trigger config]
  (let [{:keys [on-mount listener default-event]} config]
    (mark-wired! el trigger)
    (when on-mount
      (on-mount (read-directive el
                                trigger)
                el))
    (when default-event
      ;; Read at event time, not mount time. A morph rewrites the attribute
      ;; in place on an element it keeps, which never re-mounts.
      (let [defer! (debouncer)]
        (dom/add-event-listener el
                                default-event
                                (fn [e]
                                  (let [directive (read-directive el
                                                                  trigger)
                                        ms        (:debounce directive)
                                        run!      #(listener e
                                                             directive
                                                             el)]
                                    (if (pos-int? ms)
                                      (do
                                        ;; The event is spent by the time
                                        ;; the timer fires, so its default
                                        ;; has to go now or not at all.
                                        (.preventDefault e)
                                        (.stopPropagation e)
                                        (defer! ms
                                                run!))
                                      (run!)))))))))

(defn mount!
  "Wires every registered trigger found on `root` or under it."
  ([]
   (mount! js/document))
  ([root]
   (doseq [[trigger config] @triggers/registry
           el    (dom/triggered-elements root trigger)
           :when (not (contains? (wired el) trigger))]
     (wire! el trigger config))))

(defonce ^:private observer
  (atom nil))

(defn- mount-added-nodes! [records]
  (doseq [record (array-seq records)
          node   (array-seq (.-addedNodes record))
          :when  (dom/element? node)]
    (mount! node)))

(defn observe!
  "Mounts every element added to the page after the initial scan.

  A swap is one such addition, so no swapping code calls `mount!` itself."
  ([]
   (observe! js/document.body))
  ([root]
   (when-not @observer
     (let [obs (js/MutationObserver. mount-added-nodes!)]
       (.observe obs
                 root
                 #js {:childList true
                      :subtree   true})
       (reset! observer obs)))))

(defn disconnect! []
  (when-let [obs @observer]
    (.disconnect obs)
    (reset! observer nil)))
