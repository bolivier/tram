(ns ^:public tram.routes
  "This is part of the public api of Tram.

  Here are fns and vars related to routing."
  (:require [malli.dev.pretty :as pretty]
            [potemkin :refer [import-vars]]
            [reitit.core :as r]
            [reitit.http :as http]
            [reitit.http.coercion]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.multipart]
            [reitit.http.interceptors.parameters]
            [reitit.ring]
            [tram.csrf]
            [tram.html]
            [tram.impl.http]
            [tram.impl.router :refer [coerce-route-entries-to-specs map-routes]]
            [tram.logging :as log]
            [tram.rendering]
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
             [tram.rendering
              expand-header-routes-interceptor
              wrap-page-interceptor
              render-template-interceptor
              render]
             [tram.csrf csrf-interceptor csrf-hidden-field csrf-meta-tag])

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
