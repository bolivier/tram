(ns rhizome.triggers
  (:require [rhizome.directives :refer [execute]]
            [rhizome.signals :as signals]))

(defonce registry
  (atom {}))

(defn register! [config]
  (swap! registry assoc
    (:attribute config)
    config))

(def bind
  {:attribute     :rhizome.core/bind
   :on-mount      (fn [directive el]
                    (let [[signal-name signal-init] directive]
                      (signals/register! signal-name signal-init)
                      (set! (.-value el) signal-init)
                      (signals/listen! signal-name
                                       (fn [new-value]
                                         (set! (.-value el) new-value)))))
   :default-event :event/input
   :listener      (fn [e directive _el]
                    (let [[signal-name] directive]
                      (signals/put! signal-name (.. e -target -value))))})

(def text
  {:attribute :rhizome.core/text
   :on-mount  (fn [directive el]
                (let [signal-key directive]
                  (signals/listen! signal-key
                                   (fn [new-value]
                                     (set! (.-textContent el) new-value)))))})

(def click
  {:attribute     :rhizome.core/click
   :default-event :event/click
   :listener      (fn [e directive el]
                    (execute (assoc directive
                               :event e
                               :el    el)))})
