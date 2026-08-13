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
            [clojure.edn :as edn]
            [malli.transform :as mt]
            [muuntaja.core :as muuntaja]
            [reitit.coercion.malli :as rcm]
            [reitit.http.coercion :as rhc]
            [reitit.http.interceptors.parameters :as parameters]
            [tram.html :as tram.html]
            [tram.impl.multipart-interceptor :as impl.multipart]
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

(def json-casing-interceptor
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

(defn parameters-interceptor
  "reitit's parameters interceptor, named `:tram/parameters`."
  []
  (assoc (parameters/parameters-interceptor) :name :tram/parameters))

(defn multipart-interceptor
  "Tram's multipart interceptor, named `:tram/multipart`.

  Parses multipart params on any request with a multipart Content-Type. See
  `tram.impl.multipart-interceptor/multipart-interceptor`."
  []
  (impl.multipart/multipart-interceptor))

(def File
  "An uploaded file, as ring's multipart temp-file store hands it over.

  `:tempfile` is deleted once the response finishes, so a handler that wants
  to keep the bytes must copy or stream them while it runs."
  [:map
   [:filename :string]
   [:content-type :string]
   [:size :int]
   [:tempfile :any]])

(def params-part
  "The multipart part rhizome puts a form's non-file fields in, as edn."
  "rhizome-params")

(defn- uploaded-file? [value]
  (and (map? value) (contains? value :tempfile)))

(defn- file-part?
  "Whether a multipart value is an upload. A control with `multiple` set
  arrives as a vector, because ring collects repeated part names."
  [value]
  (if (sequential? value)
    (and (seq value)
         (every? uploaded-file?
                 value))
    (uploaded-file? value)))

(defn- merge-file-parts [params edn-part]
  (into (edn/read-string edn-part)
        (keep (fn [[part-name value]]
                (when (file-part? value)
                  [(keyword part-name) value])))
        params))

(def rhizome-multipart-interceptor
  "Rebuilds `:body-params` from a rhizome multipart submission.

  A form carrying files cannot ride as edn, so rhizome splits it: the fields
  go in one edn part and each file gets its own. This puts them back together
  so a handler sees the same `:body-params` either way. Files arrive as ring's
  `{:filename :content-type :size :tempfile}`, and the tempfile is deleted once
  the response finishes.

  A multipart request without the edn part is left alone, so plain HTML form
  uploads still reach `[:parameters :multipart]` the way reitit puts them."
  {:name  :tram/rhizome-multipart
   :must-run-after [:tram/multipart]
   :enter (fn [ctx]
            (let [params (get-in ctx [:request :multipart-params])]
              (if-let [edn-part (get params params-part)]
                (assoc-in ctx
                  [:request :body-params]
                  (merge-file-parts params edn-part))
                ctx)))})

(defn coerce-request-interceptor
  "reitit's request-coercion interceptor, named `:tram/coerce-request`."
  []
  (assoc (rhc/coerce-request-interceptor) :name :tram/coerce-request))

(defn coerce-exceptions-interceptor
  "reitit's coercion-exception interceptor, named `:tram/coerce-exceptions`."
  []
  (assoc (rhc/coerce-exceptions-interceptor) :name :tram/coerce-exceptions))

(defn coerce-response-interceptor
  "reitit's response-coercion interceptor, named `:tram/coerce-response`."
  []
  (assoc (rhc/coerce-response-interceptor) :name :tram/coerce-response))

(defn wire-format
  "The wire-format concern-group. Returns the ordered vector of request-side
  interceptors that interpret an HTTP request as Clojure data: parameter
  parsing, multipart, coercion, and json key-casing.

  `format-interceptor` is deliberately NOT part of this group: it is the
  outermost transport codec and must sit outside the exception boundary so it
  encodes error responses too. Place `(format-interceptor)` first in the chain,
  then `(exception-interceptor)`, then this group."
  []
  [(parameters-interceptor)
   (multipart-interceptor)
   rhizome-multipart-interceptor
   (coerce-request-interceptor)
   (coerce-exceptions-interceptor)
   (coerce-response-interceptor)
   json-casing-interceptor])
