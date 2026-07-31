(ns rhizome.dom
  (:require [rhizome.events :as event]
            [rhizome.utils :refer [kw->string]]))

(extend-type js/HTMLElement
  ILookup
  (-lookup [el attr] (.getAttribute el (kw->string attr))))

(defn query-selector-triggers [root trigger]
  (.querySelectorAll root (str "[" (kw->string trigger) "]")))

(defn add-event-listener
  "Wrapper for .addEventListener

  Coerces event name from kw event to dom event."
  [el event cb]
  (.addEventListener el (event/->dom-event event) cb))
