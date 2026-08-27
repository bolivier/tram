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
            [tram.impl.http :refer [html-request? rhizome-request?]]
            [tram.rendering.template-renderer :as renderer]
            [tram.sse :as sse]))

(def expand-header-routes-interceptor
  "Expands route references in response headers."
  {:name  :tram/expand-headers
   :leave (fn [ctx]
            (let [router (get-in ctx [:request ::r/router])]
              (assert router "expand-header-routes-interceptor requires router")
              (update-in ctx
                         [:response :headers]
                         (fn [headers]
                           (prewalk #(tram.html/route-name-expander router %)
                                    headers)))))})

(defn wrap-page-interceptor
  "Wraps the returned html in a full html page (if it should).

  Does nothing if the current request is via rhizome, if it is an assets
  request, or if the handler set `:body` itself and so owns the whole response.

  `full-page-renderer` is the component for your full html page. It should
  render <head> and any other meta tags a full page reload would need for your
  application. It is called with one argument, the contents of the body tag."
  [full-page-renderer]
  {:name  :tram/wrap-page
   :must-run-after [:tram/format]
   :leave (fn [ctx]
            (let [req              (:request ctx)
                  html?            (html-request? req)
                  rhizome?         (rhizome-request? req)
                  resource?        (str/starts-with? (:uri req) "/assets")
                  needs-full-page? (and html?
                                        (not rhizome?)
                                        (not resource?)
                                        (renderer/rendered? (:response ctx)))]
              (update-in ctx
                         [:response :body]
                         (fn [body]
                           (cond
                             needs-full-page? (full-page-renderer body)
                             :else            body)))))})

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

              :else (renderer/render ctx)))})

(def ^:private render-opts-schema
  [:map [:page-wrapper fn?]])

(defn render
  "The render concern-group. Returns the ordered vector of response-side
  interceptors.

  `opts` requires:

  | key             | description |
  |-----------------|-------------|
  | `:page-wrapper` | one-arg fn wrapping body hiccup in a full html page |"
  [{:keys [page-wrapper]
    :as   opts}]
  (when-not (m/validate render-opts-schema
                        opts)
    (throw (ex-info (str "Invalid opts for tram.rendering/render: "
                         (me/humanize (m/explain render-opts-schema
                                                 opts)))
                    {:opts opts})))
  [sse/stream-response-interceptor
   expand-header-routes-interceptor
   (wrap-page-interceptor page-wrapper)
   render-template-interceptor])
