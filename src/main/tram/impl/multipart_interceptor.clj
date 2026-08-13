(ns tram.impl.multipart-interceptor
  "Multipart request handling, based on ring.middleware.multipart-params.

  Unlike reitit's multipart interceptor, which mounts only on routes that
  declare `[:parameters :multipart]`, this one activates per request: any
  request with a multipart Content-Type gets its parts parsed into
  `:multipart-params`. A route that does declare `[:parameters :multipart]`
  additionally gets those params coerced into `[:parameters :multipart]`."
  (:require [clojure.string :as str]
            [reitit.coercion :as coercion]
            [ring.middleware.multipart-params :as multipart-params])
  (:import (java.io File)))

(def temp-file-part
  "Schema for a file param created by the
  ring.middleware.multipart-params.temp-file store."
  [:map {:swagger     {:type "file"}
         :json-schema {:type   "string"
                       :format "binary"}}
   [:filename :string]
   [:content-type :string]
   [:tempfile [:fn (partial instance? File)]]
   [:size :int]])

(def bytes-part
  "Schema for a file param created by the
  ring.middleware.multipart-params.byte-array store."
  [:map {:swagger     {:type "file"}
         :json-schema {:type   "string"
                       :format "binary"}}
   [:filename :string]
   [:content-type :string]
   [:bytes [:fn bytes?]]])

(defn- multipart-request? [request]
  (some-> (get-in request [:headers "content-type"])
          (str/starts-with? "multipart/")))

(defn- coerce-multipart [request coercers]
  (if coercers
    (update request
            :parameters
            merge
            (coercion/coerce-request coercers
                                     request))
    request))

(def ^:private multipart-parameter-coercion
  {:multipart (coercion/->ParameterCoercion :multipart-params :string
                                            true true)})

(defn multipart-interceptor
  "Interceptor that parses multipart params, named `:tram/multipart`.

  Runs on every request with a multipart Content-Type and publishes the parts
  as `:multipart-params` on the request. When the route declares
  `[:parameters :multipart]`, coerces the parts into `[:parameters :multipart]`
  as well.

  `options` are passed through to
  `ring.middleware.multipart-params/multipart-params-request`."
  ([]
   (multipart-interceptor nil))
  ([options]
   {:name    :tram/multipart
    :compile (fn [{:keys [parameters coercion]} opts]
               (let [coercers (when (and coercion
                                         (:multipart parameters))
                                (coercion/request-coercers
                                  coercion
                                  (select-keys parameters
                                               [:multipart])
                                  (assoc opts
                                    ::coercion/parameter-coercion
                                    multipart-parameter-coercion)))]
                 (cond-> {:name :tram/multipart
                          :enter
                          (fn [ctx]
                            (let [request (:request ctx)]
                              (if (multipart-request? request)
                                (assoc ctx
                                  :request
                                  (-> request
                                      (multipart-params/multipart-params-request
                                        options)
                                      (coerce-multipart coercers)))
                                ctx)))}
                   coercers (assoc :data
                              {:swagger {:consumes
                                         ^:replace
                                         #{"multipart/form-data"}}}))))}))
