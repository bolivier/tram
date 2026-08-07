(ns rhizome.triggers
  (:require [rhizome.directives :refer [execute]]
            [rhizome.dom :as dom]
            [rhizome.signals :as signals]))

(defonce registry
  (atom {}))

(defn register! [config]
  (swap! registry assoc
    (:attribute config)
    config))

(def bind
  "Two-way: the control seeds the signal, then each follows the other."
  {:attribute     :rhizome.core/bind
   :on-mount      (fn [directive el]
                    (let [[signal-name signal-init] directive]
                      (signals/listen! signal-name
                                       (fn [new-value]
                                         (dom/set-bound-value! el new-value)))
                      (signals/put! signal-name signal-init)))
   :default-event :event/input
   :listener      (fn [_e directive el]
                    (let [[signal-name] directive]
                      (signals/put! signal-name (dom/bound-value el))))})

(def text
  {:attribute :rhizome.core/text
   :on-mount  (fn [directive el]
                (let [signal-key directive]
                  (signals/listen! signal-key
                                   (fn [new-value]
                                     (set! (.-textContent el)
                                           (str new-value))))))})

(def show
  "Hides the element while its signal is falsey.

  Clearing `display` hands the element back to the stylesheet rather than
  forcing a value the sheet did not ask for."
  {:attribute :rhizome.core/show
   :on-mount  (fn [directive el]
                (let [signal-key directive]
                  (prn 'show-key signal-key directive)
                  (signals/listen! signal-key
                                   (fn [new-value]
                                     (set! (.. el -style -display)
                                           (if new-value
                                             ""
                                             "none"))))))})

(def debug
  "Development aid: prints the whole signal registry into the element."
  {:attribute :rhizome.core/debug
   :on-mount  (fn [_directive el]
                (let [render! (fn [entries]
                                (set! (.-textContent el)
                                      (pr-str (update-vals entries :value))))]
                  (render! @signals/registry)
                  (cljs.pprint/pprint @signals/registry)
                  (add-watch signals/registry
                             el
                             (fn [_ _ _ entries] (render! entries)))))})

(defn- event-trigger
  "A trigger whose whole job is to run its directive when one dom event fires."
  [attribute event]
  {:attribute     attribute
   :default-event event
   :listener      (fn [e directive el]
                    (execute (assoc directive
                               :event e
                               :el    el)))})

(def click
  (event-trigger :rhizome.core/click :event/click))

(def submit
  (event-trigger :rhizome.core/submit :event/submit))

(def input
  (event-trigger :rhizome.core/input :event/input))

(def load
  "Runs its directive as soon as the element mounts, with no event to wait on.

  The response is expected to drop the attribute, or the new element mounts and
  fetches again."
  {:attribute :rhizome.core/load
   :on-mount  (fn [directive el] (execute (assoc directive :el el)))})
