(ns rhizome.dom
  (:require [clojure.string :as str]
            [rhizome.events :as event]
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

(defn closest-form [el]
  (if (= "FORM" (.-tagName el))
    el
    (.closest el
              "form")))

(defn form->map
  "A form's named controls as a map of keyword name to value.

  `.elements` holds every control the form owns, including ones tied to it by
  a `form` attribute rather than by nesting."
  [form]
  (reduce (fn [acc control]
            (let [control-name (.-name control)]
              (if (str/blank? control-name)
                acc
                (assoc acc
                  (keyword control-name) (.-value control)))))
    {}
    (array-seq (.-elements form))))

(defn add-event-listener
  "Wrapper for .addEventListener

  Coerces event name from kw event to dom event."
  [el event cb]
  (.addEventListener el (event/->dom-event event) cb))
