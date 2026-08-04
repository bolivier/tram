(ns tram.rendering.template-renderer
  "Render html templates from the ring response.

  The template to render is whatever the handler returned as `:template`,
  falling back to the one `defroutes` stamped on the route. `ITemplate` turns
  any of the shapes that may be — symbol, keyword, var, fn — into a view fn,
  and names it well enough to report on when it resolves to nothing.

  Views are resolved per request rather than when the route is compiled, so a
  view written or edited after its route still renders."
  (:require [malli.core :as m]
            [malli.error :as me]
            [reitit.core :as r]
            [rhizome.html :as h]
            [tram.impl.http :refer [rhizome-request?]]
            [tram.language :as lang]))

(defn- handler-ns
  "The namespace the matched route was defined in, stamped by `defroutes`."
  [ctx]
  (get-in ctx [:request ::r/match :data :namespace]))

(defprotocol ITemplate
  "Protocol for something that can be used as a render template.

  Only the get-view-fn function is necessary to make this code work, but error
  reporting is much simpler if there is a way to grab the ns and the name also."
  (get-view-fn [this ctx]
    "Get the view fn for this template")
  (get-name [this ctx]
    "Get the name of the template")
  (get-namespace [this ctx]
    "Get the namespace for the template"))

(defn resolve-view
  "Resolve `view-sym` to its var, or nil when no such view exists.

  A view namespace that fails to load for its own reasons still throws."
  [view-sym]
  (try
    (requiring-resolve view-sym)
    (catch java.io.FileNotFoundException _
      nil)))

(extend-protocol ITemplate
  clojure.lang.Symbol
  (get-name [this _] (name this))
  (get-namespace [this _] (namespace this))
  (get-view-fn [this _] (resolve-view this))

  clojure.lang.Keyword
  (get-name [this _] (name this))
  (get-namespace [this ctx]
    (namespace (lang/view-symbol (handler-ns ctx) this)))
  (get-view-fn [this ctx]
    (resolve-view (lang/view-symbol (handler-ns ctx) this)))

  clojure.lang.Fn
  (get-view-fn [this _] this)
  (get-namespace [this _]
    ;; kindof cheating, but this is mostly for debugging
    (str this))
  (get-name [this _]
    ;; kindof cheating, but this is mostly for debugging
    (str this))

  clojure.lang.Var
  (get-name [this _] (:name (meta this)))
  (get-namespace [this _] (str (:ns (meta this))))
  (get-view-fn [this _] this)

  nil
  (get-name [_ _] "<nil>")
  (get-namespace [_ ctx]
    (when-let [ns (handler-ns ctx)]
      (lang/convert-ns ns :view)))
  (get-view-fn [_ _] nil))

(defn uses-layout? [req]
  (not (rhizome-request? req)))

(defn make-root-layout-fn [ctx]
  (if (uses-layout? (:request ctx))
    (apply comp
      (:layouts ctx))
    identity))

(defn- effective-template
  "The template to render: the one the handler returned, else the one the route
  was compiled with."
  [ctx]
  (or (get-in ctx [:response :template])
      (get-in ctx
              [:request
               ::r/match
               :data
               (get-in ctx [:request :request-method])
               :template])))

(def ^:private content-key?
  #{:hiccup :html :body :stream})

(def response-content-schema
  "The mutually exclusive ways a handler hands content back.

  `:hiccup` is a tree to render, escaped as data. `:html` is finished markup
  emitted verbatim — from a markdown renderer, a cache, another template engine
  — and still wrapped in the layout, so it is a fragment rather than a document.
  `:body` is a Ring body the handler produced itself and owns outright.
  `:stream` is a source of events, sent as `text/event-stream` by
  `tram.sse/stream-response-interceptor`.

  A response with none of them — including no response at all — resolves a
  `:template` instead."
  [:maybe
   [:and
    [:map
     [:hiccup {:optional true}
      :any]
     [:html {:optional true}
      :string]
     [:body {:optional true}
      :any]
     [:stream {:optional true}
      :any]]
    [:fn {:error/message
          "only one of :hiccup, :html, :body, or :stream may be set"}
     (fn [response] (>= 1 (count (filter content-key? (keys response)))))]]])

(defn- validate-content! [uri response]
  (when-not (m/validate response-content-schema
                        response)
    (throw (ex-info (str "Route (" uri
                         ") returned an invalid response: "
                         (me/humanize (m/explain response-content-schema
                                                 response)))
                    {:error    :invalid-response-content
                     :uri      uri
                     :response response}))))

(defn owns-body?
  "A handler that set `:body` produced the bytes itself — a stream, a file, a
  string from another renderer — so rendering and page wrapping both step
  aside and it reaches the client untouched.

  A handler that set `:stream` owns the whole response the same way."
  [response]
  (or (contains? response :body) (contains? response :stream)))

(defn rendered?
  "True once `render` has built a body from `:hiccup`, `:html`, or a template.
  Page wrapping applies only to what the renderer made."
  [response]
  (contains? response ::rendered))

(defn- content-view-fn
  "A view fn for content the handler returned directly, or nil when it returned
  none and a template must be resolved."
  [response]
  (cond
    (contains? response :hiccup) (constantly (:hiccup response))
    (contains? response :html)   (constantly (h/raw-string (:html response)))))

(defn render
  "Renders a template."
  [ctx]
  (let [{:keys [request response]} ctx
        {:keys [locals]} response]
    (validate-content! (:uri request) response)
    (if (owns-body? response)
      ctx
      (let [template (effective-template ctx)
            view-fn  (or (content-view-fn response)
                         (get-view-fn template
                                      ctx))]
        (if-not view-fn
          (throw (ex-info (format "Route (%s) does not have a valid template."
                                  (:uri request))
                          {:error :missing-template
                           :uri (:uri request)
                           :expected-template-ns (get-namespace template
                                                                ctx)
                           :expected-template-name (get-name template
                                                             ctx)}))
          (let [layout-fn (make-root-layout-fn ctx)]
            (update ctx
                    :response  assoc
                    :body      (layout-fn (view-fn locals))
                    ::rendered true)))))))
