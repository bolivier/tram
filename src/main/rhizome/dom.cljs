(ns rhizome.dom
  (:require [rhizome.events :as event]
            [rhizome.utils :refer [kw->string]]))

(extend-type js/HTMLElement
  ILookup
  (-lookup [el attr] (.getAttribute el (kw->string attr))))

(defn element? [node]
  (= js/Node.ELEMENT_NODE (.-nodeType node)))

(defn trigger-selector [trigger]
  (str "[" (kw->string trigger) "]"))

(defn triggered-elements
  "Elements under `root` carrying `trigger`, `root` itself included.

  `querySelectorAll` never matches the node it is called on, and every node a
  `MutationObserver` reports is such a node."
  [root trigger]
  (let [selector    (trigger-selector trigger)
        descendants (array-seq (.querySelectorAll root selector))]
    (if (and (element? root)
             (.matches root
                       selector))
      (cons root
            descendants)
      descendants)))

(defn add-event-listener
  "Wrapper for .addEventListener

  Coerces event name from kw event to dom event."
  [el event cb]
  (.addEventListener el (event/->dom-event event) cb))
