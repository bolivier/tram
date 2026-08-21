(ns rhizome.behaviors
  (:require [rhizome.commands :refer [execute]]
            [rhizome.dom :as dom]
            [rhizome.expr :as expr]
            [rhizome.signals :as signals])
  (:refer-clojure :exclude [drop]))

(defonce registry
  (atom {}))

(defn register! [config]
  (swap! registry assoc
    (:attribute config)
    config))

(def bind
  "Two-way: the control seeds the signal, then each follows the other."
  {:attribute     :rhizome.core/bind
   :on-mount      (fn [payload el]
                    (let [[signal-name signal-init] payload]
                      (signals/listen! signal-name
                                       (fn [new-value]
                                         (dom/set-bound-value! el new-value)))
                      (signals/put! signal-name signal-init)))
   :default-event :event/input
   :listener      (fn [_e payload el]
                    (let [[signal-name] payload]
                      (signals/put! signal-name (dom/bound-value el))))})

(def text
  {:attribute :rhizome.core/text
   :on-mount  (fn [payload el]
                (expr/watch! payload
                             (fn [new-value]
                               (set! (.-textContent el)
                                     (str new-value)))))})

(def class
  "Adds `:class` while `:pred` evaluates truthy, removes it otherwise."
  {:attribute :rhizome.core/class
   :on-mount  (fn [payload el]
                (let [{:keys [pred class]} payload]
                  (expr/watch! pred
                               (fn [new-value]
                                 (if new-value
                                   (.add (.-classList el)
                                         class)
                                   (.remove (.-classList el)
                                            class))))))})

(def show
  "Hides the element while its expression is falsey.

  Clearing `display` hands the element back to the stylesheet rather than
  forcing a value the sheet did not ask for."
  {:attribute :rhizome.core/show
   :on-mount  (fn [payload el]
                (expr/watch! payload
                             (fn [new-value]
                               (set! (.. el -style -display)
                                     (if new-value
                                       ""
                                       "none")))))})

(def debug
  "Development aid: prints the whole signal registry into the element."
  {:attribute :rhizome.core/debug
   :on-mount  (fn [_payload el]
                (let [render! (fn [entries]
                                (set! (.-textContent el)
                                      (pr-str (update-vals entries :value))))]
                  (render! @signals/registry)
                  (cljs.pprint/pprint @signals/registry)
                  (add-watch signals/registry
                             el
                             (fn [_ _ _ entries] (render! entries)))))})

(defn- event-trigger
  "A behavior whose whole job is to run its command when one dom event fires."
  [attribute event]
  {:attribute     attribute
   :default-event event
   :listener      (fn [e command el]
                    (execute (assoc command
                               :event e
                               :el    el)))})

(def click
  (event-trigger :rhizome.core/click :event/click))

(def submit
  (event-trigger :rhizome.core/submit :event/submit))

(def input
  (event-trigger :rhizome.core/input :event/input))

(def drop
  (event-trigger :rhizome.core/drop :event/drop))

(def dragover
  (event-trigger :rhizome.core/dragover :event/dragover))

(def load
  "Runs its command as soon as the element mounts, with no event to wait on.

  The response is expected to drop the attribute, or the new element mounts and
  fetches again."
  {:attribute :rhizome.core/load
   :on-mount  (fn [command el] (execute (assoc command :el el)))})
