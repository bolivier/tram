(ns rhizome.directives
  (:require ["idiomorph" :refer [Idiomorph]]
            [clojure.string :as str]))

(defmulti execute
  :do)

(defmethod execute :default
  [directive]
  (println "rhizome.core/execute not implemented for operation"
           (:do directive)))

(defn execute-http [{:keys [http/method http/url event]
                     :as   _directive}]
  (when event
    (.preventDefault event)
    (.stopPropagation event))
  (let [resp-p (js/fetch url
                         (clj->js {:method  (str/upper-case (name method))
                                   :headers {"rhizome-request" "true"}}))]
    (-> resp-p
        (.then (fn [resp] (.text resp)))
        (.then (fn [body]
                 (execute {:do :dom/morph
                           :dom/content body}))))))

(defmethod execute :http/post
  [directive]
  (execute-http (assoc directive :http/method :post)))

(defn parse-fragment
  "Parses an HTML string into a seq of its top-level elements.

  `template` is the only parse context that accepts any content, so bare
  `<tr>` and `<td>` survive."
  [html]
  (let [tpl (js/document.createElement "template")]
    (set! (.-innerHTML tpl) html)
    (array-seq (.. tpl -content -children))))

(defn morph-target
  "Finds the live element a response element replaces.

  Every top-level element in a response is matched by id. An element with no
  id, or an id that is not on the page, matches nothing."
  [element]
  (some-> (not-empty (.-id element))
          js/document.getElementById))

(defmethod execute :dom/morph
  [{:keys [dom/content]}]
  (doseq [element (parse-fragment content)]
    (if-let [target (morph-target element)]
      (.morph Idiomorph target element #js {:morphStyle "outerHTML"})
      (js/console.warn
        "rhizome: dropped a response element, no element on the page has id"
        (pr-str (.-id element))
        element))))
