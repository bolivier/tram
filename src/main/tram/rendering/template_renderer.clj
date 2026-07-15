(ns tram.rendering.template-renderer
  "Render html templates from the ring response.

  The template to render is whatever the handler returned as `:template`,
  falling back to the one `defroutes` stamped on the route. `ITemplate` turns
  any of the shapes that may be — symbol, keyword, var, fn — into a view fn,
  and names it well enough to report on when it resolves to nothing.

  Views are resolved per request rather than when the route is compiled, so a
  view written or edited after its route still renders."
  (:require [reitit.core :as r]
            [tram.impl.http :refer [boosted-request? htmx-request?]]
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

(defn- resolve-view
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
  (cond
    (boosted-request? req) true
    (htmx-request? req)    false
    :else                  true))

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

(defn render
  "Renders a template."
  [ctx]
  (let [{:keys [request response]} ctx
        {:keys [locals]} response
        template         (effective-template ctx)
        view-fn          (if-let [body (:body response)]
                           (constantly body)
                           (get-view-fn template ctx))]
    (if-not view-fn
      (throw
        (ex-info
          (str
            "Route ("
            (:uri request)
            ") does not have a valid template.

Expected to find template called `"
            (get-name template ctx)
            "` at: "
            (get-namespace template ctx))
          {:error         :no-template
           :uri           (:uri request)
           :template      template
           :template-name (get-name template ctx)}))
      (let [layout-fn (make-root-layout-fn ctx)]
        (assoc-in ctx [:response :body] (layout-fn (view-fn locals)))))))
