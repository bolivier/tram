(ns tram.wire-format
  "The wire-format concern: translating between the bytes on the HTTP wire and
  Clojure data.

  Owns the muuntaja instance assembly, request/response coercion, and the
  content-negotiation and json-casing interceptors. The low-level hiccup/form
  encoders live in `tram.html`; this namespace composes them into a muuntaja
  instance and the interceptors that use it.

  Reexported from `tram.routes` for external use."
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [malli.transform :as mt]
            [muuntaja.core :as muuntaja]
            [reitit.coercion.malli :as rcm]
            [tram.html :as tram.html]
            [tram.vars :refer [*current-user* *req* *res*]]))

(defn make-muuntaja-instance
  "make a muuntaja instance with default options.

  Includes an html formatter, a urlencoded formatter, and sets the default
  format tho text/html.

  Options are merged the map fed to `muuntaja.core/create` last."
  ([]
   (make-muuntaja-instance {}))
  ([options]
   (-> muuntaja/default-options
       (assoc-in [:formats "text/html"] tram.html/html-formatter)
       (assoc-in [:formats "application/x-www-form-urlencoded"]
                 tram.html/form-urlencoded-formatter)
       (assoc :default-format "text/html")
       (merge options)
       (muuntaja/create))))

(defn string->vector-transformer []
  (mt/transformer {:name     :string->vector
                   :decoders {:vector {:compile (fn [_schema _]
                                                  (fn [value]
                                                    (if (string? value)
                                                      [value]
                                                      value)))}}}))

(def ^:private string->vector-transformer-provider
  (reify
    rcm/TransformationProvider
    (-transformer [_ {:keys [strip-extra-keys default-values]}]
      (mt/transformer (when strip-extra-keys
                        (mt/strip-extra-keys-transformer))
                      (string->vector-transformer)
                      (mt/string-transformer)
                      (when default-values
                        (mt/default-value-transformer))))))

(def coercion
  "Adds coercion to the default coercion object from `reitit.coercion.malli`.

  Note, this assumes that the router is also constructed with the muuntaja
  formatter that converts form data into body-params."
  (rcm/create
    (assoc-in rcm/default-options
      [:transformers :body :formats "application/x-www-form-urlencoded"]
      string->vector-transformer-provider)))

(def format-json-body-interceptors
  {:name  :tram/json-casing
   :enter (fn [ctx]
            (let [ct (get-in ctx [:request :muuntaja/request :format])]
              (cond
                (= "application/json" ct)
                (update-in ctx
                           [:request :body-params]
                           (partial transform-keys csk/->kebab-case-keyword))

                :else ctx)))
   :leave (fn [ctx]
            (let [ac (get-in ctx [:request :headers "accept"])]
              (cond
                (= "application/json" ac)
                (update-in ctx
                           [:response :body]
                           (partial transform-keys csk/->camelCaseString))

                :else ctx)))})

(defn format-interceptor
  "Interceptor for content-negotiation, request and response formatting.

  Negotiates a request body based on `Content-Type` header and response body based on
  `Accept`, `Accept-Charset` headers. Publishes the negotiation results as `:muuntaja/request`
  and `:muuntaja/response` keys into the request.

  Decodes the request body into `:body-params` using the `:muuntaja/request` key in request
  if the `:body-params` doesn't already exist.

  Encodes the response body using the `:muuntaja/response` key in request if the response
  doesn't have `Content-Type` header already set.

  Optionally takes a default muuntaja instance as argument.

  | key          | description |
  | -------------|-------------|
  | `:muuntaja`  | `muuntaja.core/Muuntaja` instance. If not set, a default instance is created."
  ([]
   (format-interceptor nil))
  ([default-muuntaja]
   {:name    :tram/format
    :compile (fn [{:keys [muuntaja]} _]
               (when-let [prototype (or muuntaja
                                        default-muuntaja
                                        (make-muuntaja-instance))]
                 (let [m (muuntaja/create prototype)]
                   {:name  :tram/format
                    :enter (fn [ctx]
                             (let [request (:request ctx)]
                               (assoc ctx
                                 :request (muuntaja/negotiate-and-format-request
                                            m
                                            request))))
                    :leave (fn [ctx]
                             (let [request  (:request ctx)
                                   response (:response ctx)]
                               (binding [*current-user* (:current-user request)
                                         *req*          request
                                         *res*          response]
                                 (assoc ctx
                                   :response (muuntaja/format-response
                                               m
                                               request
                                               response)))))})))}))
