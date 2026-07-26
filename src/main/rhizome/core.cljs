(ns rhizome.core
  (:require ["idiomorph" :as idiomorph]
            [cljs.core.async :refer [<! chan go-loop put!] :include-macros true]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [promesa.core :as p]
            [rhizome.command :refer [execute]]))

(defn- get-root-node [el]
  (if el
    (.getRootNode el)
    js/document))

(defn precedes? [el other]
  (= (.compareDocumentPosition el other)
     (.-DOCUMENT_POSITION_PRECEDING js/Node)))

(defn follows? [el other]
  (= (.compareDocumentPosition el other)
     (.-DOCUMENT_POSITION_FOLLOWING js/Node)))

(defmulti get-target
  "Get the target identified by a cmd's :ident key.

  `el` is the dom node the cmd is on
  `ident` is the :ident value from a command"
  (fn [el ident] (first ident)))

(defmethod get-target :default
  [_ _]
  nil)

(defmethod get-target :id
  [el [_ id]]
  (.querySelector (get-root-node el) (str "#" id)))

(defmethod get-target :closest
  [el [_ specifier]]
  (.closest el (name specifier)))

(defmethod get-target :previous
  [el [_ specifier]]
  (if-not specifier
    (.-previousElementSibling el)
    (->> (.querySelectorAll (get-root-node el) specifier)
         reverse
         (filter #(precedes? el %))
         first)))

(defmethod get-target :next
  [el [_ specifier]]
  (if-not specifier
    (.-nextElementSibling el)
    (->> (.querySelectorAll (get-root-node el) specifier)
         (filter #(follows? el %))
         first)))

(defmethod get-target :this
  [el _]
  el)

(defmethod execute :dom/remove
  [el cmd]
  (let [target (get-target el (:ident cmd))]
    (.remove target)))

(defn form->map
  "Serialize a <form>'s named controls into a map of keyword name -> value."
  [form]
  (when form
    (reduce (fn [acc el]
              (let [n (.-name el)]
                (if (and n
                         (not= ""
                               n))
                  (assoc acc
                    (keyword n) (.-value el))
                  acc)))
      {}
      (array-seq (.-elements form)))))

(defmulti get-body
  "Collect the content to send in the body of an http request.  Dispatches on
  the first element of a body spec vector, mirroring `get-target`.

  - `root` is the root dom node
  - `el`   is the dom node the command is on
  - `spec` is the body spec, e.g. [:form] or [:form [:id \"my-form\"]]

  Methods return a Clojure value (sent as edn) or nil for no body.  Extend this
  multimethod to support new ways of grabbing request content."
  (fn [el spec] (first spec)))

(defmethod get-body :form
  [el [_ selector]]
  (form->map (cond
               selector (get-target el selector)
               (= "FORM" (.-tagName el)) el
               :else (.closest el "form"))))

(defmethod get-body :value
  [el value-literal]
  value-literal)

(defmethod get-body :default
  [_ _]
  nil)

(defn resolve-body
  "Resolve the request body for an http command.  Uses an explicit `:http/body`
  spec when present, otherwise infers a form body when the element is (or sits
  inside) a <form>."
  [el {:keys [http/body]}]
  (or body
      (when-let [spec (when (or (= "FORM" (.-tagName el))
                                (.closest el
                                          "form"))
                        [:form])]
        (get-body el spec))))

(def request-header
  "Header rhizome sets on every request it drives, so the server can tell a
  rhizome fetch apart from a full page load and answer with a fragment instead
  of a whole page."
  "rhizome-request")

(defn fetch [{:keys [url method :body headers]
              :as   fetch-args}]
  (let [params (cond-> {:method (str/upper-case (name method))}
                 (not= :get method) (assoc :body (str body))
                 true (assoc :headers
                        (merge {request-header "true"
                                :content-type  "application/edn"}
                               headers)))]
    (js/fetch url (clj->js params))))

(defn execute-http [el
                    {:keys [http/method http/url event]
                     :as   cmd}]
  (when event
    (.preventDefault event)
    (.stopPropagation event))
  (p/catch (p/let [resp (fetch {:url    url
                                :method method
                                :body   (resolve-body el nil)})]
             (let [content-type (.get (.-headers resp) "content-type")]
               (cond
                 (re-find #"text/html" content-type)
                 (p/let [body (.text resp)]
                   (execute el
                            (merge cmd
                                   {:op :dom/morph
                                    :dom/content body})))

                 #_(= content-type "text/event-stream")
                 #_(let [body   (.-body resp)
                         reader (.getReader body)]
                     (p/loop []
                       (p/let [chunk (.read reader)]
                         (if (.-done chunk)
                           nil
                           (let [value (decode-chunk chunk)]
                             (when (= (:event value) 'rhizome-patch-element)
                               (doseq [html (get-in value
                                                    [:data :elements])]
                                 (execute el
                                          (merge cmd
                                                 {:op          :dom/morph
                                                  :ident       nil
                                                  :dom/content html}))))
                             (p/recur)))))))))
    (fn [err] (js/console.log err))))

(defmethod execute :http/get
  [el cmd]
  (execute-http el (assoc cmd :http/method :get)))

(defmethod execute :http/post
  [el cmd]
  (execute-http el (assoc cmd :http/method :post)))

(defmethod execute :http/put
  [el cmd]
  (execute-http el (assoc cmd :http/method :put)))

(defmethod execute :http/patch
  [el cmd]
  (execute-http el (assoc cmd :http/method :patch)))

(defmethod execute :http/delete
  [el cmd]
  (execute-http el (assoc cmd :http/method :delete)))

(declare wire-subtree!)

(defmethod execute :dom/morph
  [el {:keys [ident dom/content]}]
  (let [children (.-children (.-body (.parseFromString (js/DOMParser.)
                                                       content
                                                       "text/html")))]
    (doseq [content children]
      (let [ident  (if (nil? ident)
                     [:id
                      (.getAttribute content
                                     "id")]
                     ident)
            target (get-target el ident)]
        (cond
          (= (first ident) :end-of)
          (do (.appendChild (js/document.querySelector (second ident))
                            content)
              (wire-subtree! content))

          target
          (.morph idiomorph/Idiomorph
                  target
                  content
                  #js {:callbacks  #js {:afterNodeAdded #(wire-subtree! %)}
                       :morphStyle "outerHTML"})

          :else (println "Found no target" ident content content))))))

(defmethod execute :dom/add-class
  [el {:keys [ident class]}]
  (if-let [target (get-target el ident)]
    (.add (.-classList target) class)
    (println (str "Could not find target " ident " to " el))))

(defmethod execute :dom/clear-attribute
  [el {:keys [dom/attribute ident]}]
  (when-let [target (get-target el ident)]
    (.removeAttribute target (name attribute))))

(defmethod execute :dom/set-attribute
  [el {:keys [dom/attribute ident dom/value]}]
  (when-let [target (get-target el ident)]
    (.setAttribute target (name attribute) (str value))))



(defmethod execute :default
  [_ {:keys [op]}]
  (println "noop on rhizome.core/execute - not implemented for value " op))

(def events
  "Map of rhizome event keywords to native DOM event-type strings passed to
  `addEventListener`."
  {;; mouse
   :event/click       "click"
   :event/dblclick    "dblclick"
   :event/mousedown   "mousedown"
   :event/mouseup     "mouseup"
   :event/mouseenter  "mouseenter"
   :event/mouseleave  "mouseleave"
   :event/mouseover   "mouseover"
   :event/mouseout    "mouseout"
   :event/mousemove   "mousemove"
   :event/contextmenu "contextmenu"
   ;; keyboard
   :event/keydown     "keydown"
   :event/keyup       "keyup"
   :event/keypress    "keypress"
   ;; form
   :event/submit      "submit"
   :event/change      "change"
   :event/input       "input"
   :event/focus       "focus"
   :event/blur        "blur"
   :event/focusin     "focusin"
   :event/focusout    "focusout"
   :event/reset       "reset"
   :event/select      "select"
   ;; clipboard
   :event/copy        "copy"
   :event/cut         "cut"
   :event/paste       "paste"
   ;; window / misc
   :event/scroll      "scroll"
   :event/resize      "resize"
   :event/load        "load"})

(defn get-inferred-event [el]
  (let [event (case (.-tagName el)
                "BUTTON"   :event/click
                "FORM"     :event/submit
                "SELECT"   :event/change
                "TEXTAREA" :event/input
                "A"        :event/click
                "INPUT"    (case (.-type el)
                             "button"   :event/click
                             "submit"   :event/click
                             "checkbox" :event/change
                             "radio"    :event/change
                             "text"     :event/input
                             "search"   :event/input
                             "email"    :event/input
                             "number"   :event/input
                             :event/change)
                :event/click)]
    (get events event)))

(defn get-event [event-kw el]
  (if event-kw
    (get events
         event-kw)
    (get-inferred-event el)))

(defn extract-command [el]
  (when-let [command (.getAttribute el "rhizome_core___on")]
    (try
      (clojure.edn/read-string command)
      (catch js/Error e
        (throw (ex-info "Could not parse `rhizome_core___on` edn"
                        {:err e
                         ::on command}))))))

(def key-names
  "Convenient keywords that map to their real html counterpart names, i.e. the
  values a `KeyboardEvent`'s `.key` reports. Used to resolve `:on/key` filters."
  #:key{:enter     "Enter"
        :escape    "Escape"
        :tab       "Tab"
        :space     " "
        :backspace "Backspace"
        :delete    "Delete"
        :up        "ArrowUp"
        :down      "ArrowDown"
        :left      "ArrowLeft"
        :right     "ArrowRight"
        :home      "Home"
        :end       "End"
        :page-up   "PageUp"
        :page-down "PageDown"})

(def config
  {:op/attribute ::on
   :op/handler   execute
   :op/filters   {:on/key (fn [e key] (= (.-key e) (get key-names key)))}
   :op/wrappers  {:on/debounce (fn [ms] (fn [handler] (fn [e] (handler e))))
                  :on/key      (fn [key]
                                 (fn [handler]
                                   (fn [e]
                                     (when (= (.-key e) (get key-names key))
                                       (handler e)))))}})

(defn- wire-el! [el config]
  (when-not (unchecked-get el
                           "rhizomeWired")
    (unchecked-set el
                   "rhizomeWired"
                   true)
    (let [command (extract-command el)]
      (if-let [event-type (get-event (:on command)
                                     el)]
        (let [filterer (fn [e]
                         (let [filters      (:op/filters config)
                               filter-preds (select-keys filters
                                                         (keys command))]
                           (reduce (fn [result [key pred]]
                                     (when result
                                       (let [arg (get command
                                                      key)]
                                         (pred e
                                               arg))))
                             true
                             filter-preds)))
              run      (fn [e]
                         (when (filterer e)
                           (execute el
                                    (assoc command
                                      :event e))))
              handler  run]
          ;; TODO: fix this hack
          (if (= "load" event-type)
            (handler nil)
            (.addEventListener el
                               event-type
                               handler)))
        (println (str "Tried to use unknown event type "
                      (:on command)))))))

(defn wire-subtree! [node]
  (when (= 1 (.-nodeType node))
    (when (.getAttribute node
                         "rhizome_core___on")
      (wire-el! node
                config))
    (doseq [el (.querySelectorAll node
                                  "[rhizome_core___on]")]
      (wire-el! el
                config))))

(defn add-on-listener [root]
  (doseq [el (.querySelectorAll root "[rhizome_core___on]")]
    (wire-el! el config)))

(defn ^:exported init
  "Entry point for the distributable drop-in script. Scans the document for
  rhizome commands and wires up listeners, deferring until the DOM is ready
  (mirrors htmx's auto-bootstrap on load)."
  []
  (if (= "loading" (.-readyState js/document))
    (.addEventListener js/document
                       "DOMContentLoaded"
                       (fn [_]
                         (add-on-listener js/document)))
    (add-on-listener js/document)))

(defn -main [])
