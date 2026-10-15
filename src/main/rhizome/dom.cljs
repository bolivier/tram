(ns rhizome.dom
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
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

(defn- control-name [control]
  (.getAttribute control "name"))

(defn- field?
  "Whether `control` contributes a field to its form's edn, checked or not."
  [control]
  (and (not (str/blank? (control-name control)))
       (not (.matches control ":disabled"))
       (not= "BUTTON" (.-tagName control))
       (not (contains? unsubmittable-types (.-type control)))))

(def ^:private numeric-types
  #{"number" "range"})

(defn control-value
  "A control's value. A multi-select carries one per selected option, and a
  number or range input carries a number, nil when empty."
  [control]
  (cond
    (= "select-multiple" (.-type control))
    (mapv #(.-value %)
      (array-seq (.-selectedOptions control)))

    (contains? numeric-types (.-type control))
    (let [number (.-valueAsNumber control)]
      (when-not (js/isNaN number)
        number))

    :else (.-value control)))

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

(def ^:private declared-types
  #{:int :vector :set [:vector :int] [:set :int]})

(defn- declared-type [control]
  (some-> (get control :rhizome.core/type)
          edn/read-string))

(defn- field-type [field-name controls]
  (let [types (set (map declared-type controls))
        type  (first types)]
    (cond
      (< 1 (count types))
      (throw (ex-info "rhizome: controls sharing a name declare different rz/types"
                      {:name field-name :types types}))

      (and (some? type)
           (not (contains? declared-types type)))
      (throw (ex-info "rhizome: unknown rz/type"
                      {:name field-name :type type}))

      :else type)))

(defn- collection-kind [type controls]
  (cond
    (contains? #{:vector :set} type)               type
    (vector? type)                                 (first type)
    (= "select-multiple" (.-type (first controls))) :vector))

(defn- element-type [type]
  (if (vector? type)
    (second type)
    (when (= :int type)
      :int)))

(defn- cast-int [value]
  (let [number (if (string? value)
                 (when (re-matches #"-?\d+" value)
                   (js/Number value))
                 value)]
    (cond
      (= "" value)                     nil
      (js/Number.isSafeInteger number) number
      :else                            value)))

(defn- cast-value [element-type value]
  (if (= :int element-type)
    (cast-int value)
    value))

(defn- checkable? [control]
  (contains? #{"checkbox" "radio"} (.-type control)))

(defn- collected-values [control]
  (cond
    (= "select-multiple" (.-type control)) (control-value control)
    (checkable? control)                   (when (.-checked control)
                                             [(.-value control)])
    :else                                  [(control-value control)]))

(defn- single-value [controls]
  (let [control (first controls)]
    (cond
      (= "radio" (.-type control))
      (some #(when (.-checked %) (.-value %)) controls)

      (and (checkbox? control)
           (not (.hasAttribute control "value")))
      (.-checked control)

      (checkbox? control)
      (when (.-checked control)
        (.-value control))

      :else (control-value control))))

(defn- field-value [field-name controls]
  (let [type (field-type field-name controls)
        kind (collection-kind type controls)
        cast (partial cast-value (element-type type))]
    (cond
      kind
      (into (if (= :set kind) #{} [])
            (comp (mapcat collected-values)
                  (map cast))
            controls)

      (and (< 1 (count controls))
           (not-every? #(= "radio" (.-type %)) controls))
      (throw (ex-info "rhizome: controls sharing a name need a collection rz/type"
                      {:name field-name}))

      :else (cast (single-value controls)))))

(defn- field-key [field-name]
  (let [k (keyword field-name)]
    (if (= k (try (edn/read-string (pr-str k))
                  (catch :default _ nil)))
      k
      (throw (ex-info "rhizome: a field name must read back as an edn keyword"
                      {:name field-name})))))

(defn form->map
  "A form's fields as a map of keyword name to edn value.

  `.elements` holds every control the form owns, including ones tied to it by
  a `form` attribute rather than by nesting. A field's shape follows its markup
  and its `rz/type`, as the rhizome spec's FormEncoding contract lays out."
  [form]
  (into {}
        (map (fn [[field-name controls]]
               [(field-key field-name) (field-value field-name controls)]))
        (group-by control-name
                  (filter field? (array-seq (.-elements form))))))

(defn form->files
  "A form's file controls as a map of keyword name to a vector of `File`s.

  A control with nothing selected contributes no entry, so an untouched file
  input leaves its key absent rather than sending an empty part."
  [form]
  (reduce (fn [acc control]
            (let [files (array-seq (.-files control))]
              (if (or (str/blank? (control-name control))
                      (.-disabled control)
                      (empty? files))
                acc
                (assoc acc
                  (keyword (control-name control)) (vec files)))))
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
