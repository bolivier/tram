(ns tram.rendering
  "The render concern-group: turning a handler's returned template or hiccup into
  a response body.

  Owns the response-side interceptors (route-reference expansion, full-page
  layout wrapping, template rendering) and the `render` group that composes them.
  The actual template resolution and rendering lives in
  `tram.rendering.template-renderer`; this namespace is the concern's public
  face, reexported from `tram.routes`."
  (:require [clojure.string :as str]
            [clojure.walk :refer [prewalk]]
            [malli.core :as m]
            [malli.error :as me]
            [reitit.core :as r]
            [tram.html :as tram.html]
            [tram.impl.http :refer [html-request? htmx-request?]]
            [tram.rendering.template-renderer :as renderer]
            [tram.vars :refer [*current-user* *req* *res*]]))

(def expand-header-routes-interceptor
  "Expands route references in response headers."
  {:name  :tram/expand-headers
   :leave (fn [ctx]
            (let [router (get-in ctx [:request ::r/router])]
              (assert router "expand-header-routes-interceptor requires router")
              (binding [*current-user* (get-in ctx [:request :current-user])
                        *req*          (:request ctx)
                        *res*          (:response ctx)]
                (-> ctx
                    (update-in [:response :headers]
                               (fn [headers]
                                 (prewalk #(tram.html/route-name-expander router
                                                                          %)
                                          headers)))))))})

(defn wrap-page-interceptor
  "Wraps the returned html in a full html page (if it should).

  Does nothing if the current request is via htmx, or if it is an assets
  request.

  `full-page-renderer` is the component for your full html page. It should
  render <head> and any other meta tags a full page reload would need for your
  application. It is called with one argument, the contents of the body tag."
  [full-page-renderer]
  {:name  :tram/wrap-page
   :must-run-after [:tram/format]
   :leave (fn [ctx]
            (let [req       (:request ctx)
                  html?     (html-request? req)
                  htmx?     (htmx-request? req)
                  resource? (str/starts-with? (:uri req) "/assets")
                  needs-full-page? (and html? (not htmx?) (not resource?))]
              (update-in ctx
                         [:response :body]
                         (fn [body]
                           (cond
                             needs-full-page?
                             (let [f full-page-renderer]
                               (f body))

                             :else body)))))})

(def render-template-interceptor
  {:name  :tram/render-template
   :must-run-after [:tram/format :tram/wrap-page]
   :leave (fn [ctx]
            (cond
              (or (str/starts-with? (get-in ctx [:request :uri]) "/assets")
                  (<= 300 (get-in ctx [:response :status] 300) 399))
              ctx

              (re-find #"application/json"
                       (get-in ctx [:request :headers "accept"] ""))
              (update ctx
                      :response
                      (fn [res]
                        (assoc res
                          :body (:data res))))

              :else
              (binding [*current-user* (get-in ctx [:request :current-user])
                        *req*          (get ctx :request)
                        *res*          (get ctx :response)]
                (renderer/render ctx))))})

(def ^:private render-opts-schema
  [:map [:page-wrapper fn?]])

(defn render
  "The render concern-group. Returns the ordered vector of response-side
  interceptors.

  `opts` requires:

  | key             | description |
  |-----------------|-------------|
  | `:page-wrapper` | one-arg fn wrapping body hiccup in a full html page |"
  [opts]
  (when-not (m/validate render-opts-schema
                        opts)
    (throw (ex-info (str "Invalid opts for tram.rendering/render: "
                         (me/humanize (m/explain render-opts-schema
                                                 opts)))
                    {:opts opts})))
  [expand-header-routes-interceptor
   (wrap-page-interceptor (:page-wrapper opts))
   render-template-interceptor])
