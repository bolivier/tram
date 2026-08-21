(ns rhizome.dom
  (:require [clojure.string :as str]
            [rhizome.events :as event]
            [rhizome.utils :refer [kw->string]]))

(extend-type js/HTMLElement
  ILookup
  (-lookup [el attr] (.getAttribute el (kw->string attr))))

(defn element? [node]
  (= js/Node.ELEMENT_NODE (.-nodeType node)))

(defn attribute-selector [attribute]
  (str "[" (kw->string attribute) "]"))

(defn elements-with-attribute
  "Elements under `root` carrying `attribute`, `root` itself included.

  `querySelectorAll` never matches the node it is called on, and every node a
  `MutationObserver` reports is such a node."
  [root attribute]
  (let [selector    (attribute-selector attribute)
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

(def ^:private unsubmittable-types
  "Control types a browser leaves out of a form submission, plus `file`, which
  has no edn representation and rides as its own multipart part instead."
  #{"button" "file" "image" "reset" "submit"})

(defn submittable?
  "Whether a browser would include `control` when submitting its form."
  [control]
  (and (not (str/blank? (.-name control)))
       (not (.-disabled control))
       (not= "BUTTON" (.-tagName control))
       (not (contains? unsubmittable-types (.-type control)))
       (or (not (contains? #{"checkbox" "radio"} (.-type control)))
           (.-checked control))))

(defn control-value
  "A control's value. A multi-select carries one per selected option."
  [control]
  (if (= "select-multiple" (.-type control))
    (mapv #(.-value %)
      (array-seq (.-selectedOptions control)))
    (.-value control)))

(defn- checkbox? [control]
  (= "checkbox" (.-type control)))

(defn- file-control? [control]
  (= "file" (.-type control)))

(defn bound-value
  "The value a control contributes to the signal it binds.

  A checkbox holds its state in `checked` and a file input in the names of
  its `files`: nil for none, the name for one, a vector under `multiple`.
  Every other control holds it in `value`."
  [control]
  (cond
    (checkbox? control) (.-checked control)

    (file-control? control)
    (let [names (mapv #(.-name %)
                  (array-seq (.-files control)))]
      (case (count names)
        0 nil
        1 (first names)
        names))

    :else (control-value control)))

(defn set-bound-value!
  "Writes a signal's value back onto the control bound to it.

  A file input accepts no programmatic write, so nil clears it and any other
  value leaves it alone."
  [control value]
  (cond
    (checkbox? control)     (set! (.-checked control)
                                  (boolean value))
    (file-control? control) (when (nil? value)
                              (set! (.-value control)
                                    ""))
    :else                   (set! (.-value control)
                                  (if (nil? value)
                                    ""
                                    value))))

(defn- collect-value [acc control-name value]
  (if (contains? acc
                 control-name)
    (update acc
            control-name
            (fn [seen]
              (conj (if (vector? seen)
                      seen
                      [seen])
                    value)))
    (assoc acc
      control-name value)))

(defn form->map
  "A form's submittable controls as a map of keyword name to value.

  `.elements` holds every control the form owns, including ones tied to it by
  a `form` attribute rather than by nesting. Names used more than once collect
  into a vector, the way checkbox groups arrive."
  [form]
  (reduce (fn [acc control]
            (collect-value acc
                           (keyword (.-name control))
                           (control-value control)))
    {}
    (filter submittable? (array-seq (.-elements form)))))

(defn form->files
  "A form's file controls as a map of keyword name to a vector of `File`s.

  A control with nothing selected contributes no entry, so an untouched file
  input leaves its key absent rather than sending an empty part."
  [form]
  (reduce (fn [acc control]
            (let [files (array-seq (.-files control))]
              (if (or (str/blank? (.-name control))
                      (.-disabled control)
                      (empty? files))
                acc
                (assoc acc
                  (keyword (.-name control)) (vec files)))))
    {}
    (filter file-control? (array-seq (.-elements form)))))

(defn csrf-token
  "The token the layout's csrf meta tag carries, nil without one."
  []
  (some-> (js/document.querySelector "meta[name=csrf-token]")
          (.getAttribute "content")))

(defn visit!
  "Full page load. A fn rather than an inline `location.assign` so tests can
  stub navigation, which browsers make unforgeable on `location` itself."
  [url]
  (.assign js/window.location url))

(defn add-event-listener
  "Wrapper for .addEventListener

  Coerces event name from kw event to dom event."
  [el event cb]
  (.addEventListener el (event/->dom-event event) cb))
