(ns ^:public tram.routes
  "This is part of the public api of Tram.

  Here are fns and vars related to routing."
  (:require [clojure.string :as str]
            [clojure.walk :refer [prewalk]]
            [malli.dev.pretty :as pretty]
            [potemkin :refer [import-vars]]
            [reitit.core :as r]
            [reitit.http :as http]
            [reitit.http.coercion]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.multipart]
            [reitit.http.interceptors.parameters :as rhip]
            [reitit.ring]
            [tram.csrf]
            [tram.html :as tram.html]
            [tram.impl.http]
            [tram.impl.router :refer [coerce-route-entries-to-specs map-routes]]
            [tram.logging :as log]
            [tram.rendering.template-renderer :as renderer]
            [tram.vars :refer [*current-user* *req* *res*]]
            [tram.wire-format]))

(import-vars [tram.impl.http htmx-request? html-request? full-redirect redirect]
             [reitit.http.interceptors.multipart multipart-interceptor]
             [reitit.http.interceptors.parameters parameters-interceptor]
             [reitit.http.coercion
              coerce-exceptions-interceptor
              coerce-request-interceptor
              coerce-response-interceptor]
             [tram.html make-route make-path]
             [tram.wire-format
              make-muuntaja-instance
              coercion
              string->vector-transformer
              format-interceptor
              format-json-body-interceptors]
             [tram.csrf csrf-interceptor csrf-hidden-field csrf-meta-tag])

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

(defn default-error-handler
  "Default error handler for `tram.routes/exception-interceptor`."
  [exception request]
  (log/event! ::uncaught-exception
              {:data {:request        (:uri request)
                      :exception      exception
                      :exception-type (ex-data exception)}})
  {:status 504
   :body   "An unknown error occurred"})

;; This is to support catching reitit coercion requests with a tram keyword.
;; It's not strictly necessary, but you would have to update the usage of the
;; keyword below in the exception interceptor.
(derive :tram.req/coercion :reitit.coercion/request-coercion)
(derive ::default ::exception/exception)

(defn exception-interceptor
  ([]
   (exception-interceptor {}))
  ([config]
   (exception/exception-interceptor
     (merge exception/default-handlers
            {::exception/default default-error-handler
             :reitit.coercion/request-coercion
             (fn [e req]
               (let [method (get-in req [:request-method])
                     schema (get (ex-data e) :schema)
                     body   (get (ex-data e) :value)
                     error-handler-fn (get-in req
                                              [::r/match :data method :error]
                                              default-error-handler)]
                 (pretty/explain schema body)
                 (error-handler-fn schema (assoc req :body body))))}
            config))))

(defn early-response
  "Helper for early returns in interceptors.

  Clears the queue of interceptors and adds a response."
  [ctx resp]
  (assoc ctx
    :response resp
    :queue    []))

(defmacro defroutes
  "Define routes in Tram.

  Without using `defroutes`, you won't get automatic template resolution in your
  routes. If you don't care to automatically resolve template names, then you
  can use `def` to create routes.

  The purpose of this macro is to add the namespace of your handlers to the
  route data, and to add the var reference of the handler itself to the handler
  data."
  [var-name routes]
  (let [evaluated-routes routes]
    `(def ~var-name
       ~(map-routes coerce-route-entries-to-specs evaluated-routes))))

(defn tram-router
  "`reitit.http/router` with default options for tram.

  `routes` - vector of routes.
  `options` - map of possible overrides"
  ([routes]
   (tram-router routes {}))
  ([routes options]
   (http/router routes options)))
