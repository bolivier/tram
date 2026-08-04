(ns rhizome.directives
  (:require ["idiomorph" :refer [Idiomorph]]
            [clojure.string :as str]
            [rhizome.dom :as dom]
            [rhizome.sse :as sse]))

(defmulti execute
  :do)

(defmethod execute :default
  [directive]
  (println "rhizome.core/execute not implemented for operation"
           (:do directive)))

(defn request-body
  "The form `el` belongs to, as edn. An element outside a form sends no body."
  [el]
  (some-> el
          dom/closest-form
          dom/form->map
          pr-str))

(defn content-type
  "The response's media type, without its parameters."
  [response]
  (-> (.. response -headers (get "content-type"))
      (or "")
      (str/split ";")
      first
      str/trim))

(defn handle-response
  "Reads a response the way its content type says to.

  Any endpoint can stream, because the response decides and not the element."
  [response]
  (if (= "text/event-stream" (content-type response))
    (sse/consume! response execute)
    (-> (.text response)
        (.then (fn [html]
                 (execute {:do          :dom/morph
                           :dom/content html}))))))

(defn execute-http [{:keys [http/method http/url event el]
                     :as   _directive}]
  (when event
    (.preventDefault event)
    (.stopPropagation event))
  (let [body   (when (not= :get
                           method)
                 (request-body el))
        params (cond-> {:method  (str/upper-case (name method))
                        :headers (cond-> {"rhizome-request" "true"}
                                   body (assoc "content-type"
                                          "application/edn"))}
                 body (assoc :body body))]
    (-> (js/fetch url (clj->js params))
        (.then handle-response))))

(defmethod execute :http/get
  [directive]
  (execute-http (assoc directive :http/method :get)))

(defmethod execute :http/post
  [directive]
  (execute-http (assoc directive :http/method :post)))

(defmethod execute :http/patch
  [directive]
  (execute-http (assoc directive :http/method :patch)))

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

(defmethod execute :dom/remove
  [{:keys [dom/id]}]
  (some-> (js/document.getElementById id)
          .remove))

(defmethod execute :dom/morph
  [{:keys [dom/content]}]
  (doseq [element (parse-fragment content)]
    (if-let [target (morph-target element)]
      (.morph Idiomorph target element #js {:morphStyle "outerHTML"})
      (js/console.warn
        "rhizome: dropped a response element, no element on the page has id"
        (pr-str (.-id element))
        element))))
