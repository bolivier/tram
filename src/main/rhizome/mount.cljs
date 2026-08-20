(ns rhizome.mount
  "The scan, the mutation observer, and listener lifecycle."
  (:require [clojure.edn :as edn]
            [rhizome.behaviors :as behaviors]
            [rhizome.dom :as dom]
            [rhizome.utils :refer [kw->string]]))

(defn- wired [el]
  (or (unchecked-get el "rhizomeWired") #{}))

(defn- mark-wired! [el attribute]
  (unchecked-set el "rhizomeWired" (conj (wired el) attribute)))

(defn- read-payload [el attribute]
  (try
    (edn/read-string (get el attribute))
    (catch js/Error _
      (js/console.warn "rhizome: could not parse edn in"
                       (kw->string attribute)
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

(defn- wire! [el attribute behavior]
  (let [{:keys [on-mount listener default-event]} behavior]
    (mark-wired! el attribute)
    (when on-mount
      (on-mount (read-payload el
                              attribute)
                el))
    (when default-event
      ;; Read at event time, not mount time. A morph rewrites the attribute
      ;; in place on an element it keeps, which never re-mounts.
      (let [defer! (debouncer)]
        (dom/add-event-listener el
                                default-event
                                (fn [e]
                                  (let [payload (read-payload el
                                                              attribute)
                                        ms      (:debounce payload)
                                        run!    #(listener e
                                                           payload
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
  "Wires every registered behavior found on `root` or under it."
  ([]
   (mount! js/document))
  ([root]
   (doseq [[attribute behavior] @behaviors/registry
           el    (dom/elements-with-attribute root attribute)
           :when (not (contains? (wired el) attribute))]
     (wire! el attribute behavior))))

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
