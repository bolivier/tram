(ns rhizome.commands
  (:require ["idiomorph" :refer [Idiomorph]]
            [clojure.string :as str]
            [rhizome.dom :as dom]
            [rhizome.sse :as sse]))

(defmulti execute
  :do)

(defmethod execute :default
  [command]
  (println "rhizome.commands/execute not implemented for operation"
           (:do command)))

(def params-part
  "The multipart part carrying a form's non-file fields as edn."
  "rhizome-params")

(defn form-data
  "A form's fields as one edn part plus one part per selected file."
  [form files]
  (let [data (js/FormData.)]
    (.append data params-part (pr-str (dom/form->map form)))
    (doseq [[control-name control-files] files
            file control-files]
      (.append data (name control-name) file))
    data))

(defn request-body
  "The form `el` belongs to, as `{:body _ :content-type _}`.

  Fields alone go as edn. A form carrying files goes as multipart instead,
  with the fields still edn in one part, because a `File` has no edn
  representation. A `FormData` sets its own content type, boundary included,
  so that case reports none. An element outside a form sends no body."
  [el]
  (when-let [form (some-> el
                          dom/closest-form)]
    (let [files (dom/form->files form)]
      (if (empty? files)
        {:body         (pr-str (dom/form->map form))
         :content-type "application/edn"}
        {:body (form-data form
                          files)}))))

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

  Any endpoint can stream, because the response decides and not the element.
  A followed redirect becomes a full page load, so a handler answers a
  rhizome post with the same 303 it answers a plain form post with."
  [response]
  (cond
    (.-redirected response)
    (execute {:do      :dom/navigate
              :dom/url (.-url response)})

    (= "text/event-stream" (content-type response))
    (sse/consume! response execute)

    :else
    (-> (.text response)
        (.then (fn [html]
                 (execute {:do :dom/morph
                           :dom/content html}))))))

(defn execute-http [{:keys [http/method http/url event el]
                     :as   _command}]
  (when event
    (.preventDefault event)
    (.stopPropagation event))
  (let [{:keys [body content-type]} (when (not= :get
                                                method)
                                      (request-body el))
        token  (when (not= :get
                           method)
                 (dom/csrf-token))
        params (cond-> {:method  (str/upper-case (name method))
                        :headers (cond-> {"rhizome-request" "true"}
                                   content-type (assoc "content-type"
                                                  content-type)
                                   token        (assoc "x-csrf-token" token))}
                 body (assoc :body body))]
    (-> (js/fetch url (clj->js params))
        (.then handle-response))))

(defmethod execute :http/get
  [command]
  (execute-http (assoc command :http/method :get)))

(defmethod execute :http/post
  [command]
  (execute-http (assoc command :http/method :post)))

(defmethod execute :http/patch
  [command]
  (execute-http (assoc command :http/method :patch)))

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

(defmethod execute :dom/navigate
  [{:keys [dom/url]}]
  (dom/visit! url))

(defmethod execute :dom/remove
  [{:keys [dom/id]}]
  (some-> (js/document.getElementById id)
          .remove))

(defmethod execute :prevent-default
  [{:keys [event]}]
  (.preventDefault event))

(defmethod execute :dom/morph
  [{:keys [dom/content]}]
  (doseq [element (parse-fragment content)]
    (if-let [target (morph-target element)]
      (.morph Idiomorph target element #js {:morphStyle "outerHTML"})
      (js/console.warn
        "rhizome: dropped a response element, no element on the page has id"
        (pr-str (.-id element))
        element))))
