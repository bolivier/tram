(ns tram.http.router
  (:require [potemkin :refer [import-vars]]
            [reitit.coercion.malli :as rcm]
            [reitit.http :as http]
            [reitit.http.coercion
             :refer
             [coerce-request-interceptor coerce-response-interceptor]]
            [reitit.http.interceptors.exception :refer [exception-interceptor]]
            [reitit.http.interceptors.multipart :refer [multipart-interceptor]]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.parameters :as rhip]
            [reitit.ring]
            [tram.http.format :refer [make-muuntaja-instance]]
            [tram.http.interceptors
             :refer
             [as-page-interceptor
              expand-hiccup-interceptor
              inject-route-name
              render-template-interceptor]]))



(defn tram-router
  "`reitit.http/router` with default options for tram.

  `routes` - vector of routes.
  `options` - map of possible overrides"
  ([routes]
   (tram-router routes {}))
  ([routes options]
   (let [{:keys [muuntaja-instance authentication-interceptor]
          :or   {muuntaja-instance (make-muuntaja-instance)}}
         options]
     (http/router routes
                  {:data {:muuntaja     muuntaja-instance
                          :coercion     rcm/coercion
                          :interceptors [(exception-interceptor)
                                         authentication-interceptor
                                         inject-route-name
                                         (muuntaja/format-interceptor
                                           muuntaja-instance)
                                         (multipart-interceptor)
                                         expand-hiccup-interceptor
                                         as-page-interceptor
                                         (coerce-request-interceptor)
                                         (coerce-response-interceptor)
                                         (rhip/parameters-interceptor)
                                         render-template-interceptor]}}))))

(import-vars [reitit.ring ring-handler])
