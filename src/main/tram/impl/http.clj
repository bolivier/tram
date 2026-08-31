(ns tram.impl.http
  "Namespace for helpers around http.

  Commonly used functions reexported from `tram.routes`, which is preferable for
  external use."
  (:require [clojure.string :as str]
            [tram.html :refer [make-route]]))

(defn rhizome-request?
  "True when the rhizome runtime drove this request, rather than the browser
  loading a page. Rhizome sets the header on every fetch it makes."
  [req]
  (some? (get-in req [:headers "rhizome-request"])))

(defn html-request? [req]
  (str/starts-with? (get-in req [:headers "accept"] "") "text/html"))

(defn- parse-inputs
  "Only used because the inputs to the redirect fns are so weird."
  [[resp-or-route route-or-params only-params]]
  (if (map? resp-or-route)
    {:route  route-or-params
     :params only-params
     :resp   resp-or-route}
    {:route  resp-or-route
     :params route-or-params
     :resp   {}}))

(defn redirect
  "Returns a resp for a 303 redirect. Browsers follow it as a full page load;
  rhizome follows it and navigates.

  Can be called in any of these ways:

  (redirect :route/name)
  (redirect resp :route/name)
  (redirect :route/name {:id 2})
  (redirect resp :route/name {:id 2})"
  ([route]
   (redirect route {}))
  ([resp-or-route route-or-params]
   (redirect resp-or-route route-or-params {}))
  ([resp-or-route route-or-params only-params]
   (let [{:keys [route resp params]}
         (parse-inputs [resp-or-route route-or-params only-params])]
     (-> resp
         (assoc :status 303)
         (assoc-in [:headers "location"] (make-route route params))))))

(def status-codes
  "Response `:status` keywords and the numeric codes they lower to.

  The standard reason phrases, kebab-cased, plus Tram's aliases: `:redirect`
  is 303."
  {:continue 100
   :switching-protocols 101
   :processing 102
   :early-hints 103
   :ok 200
   :created 201
   :accepted 202
   :non-authoritative-information 203
   :no-content 204
   :reset-content 205
   :partial-content 206
   :multi-status 207
   :already-reported 208
   :im-used 226
   :multiple-choices 300
   :moved-permanently 301
   :found 302
   :see-other 303
   :redirect 303
   :not-modified 304
   :use-proxy 305
   :temporary-redirect 307
   :permanent-redirect 308
   :bad-request 400
   :unauthorized 401
   :payment-required 402
   :forbidden 403
   :not-found 404
   :method-not-allowed 405
   :not-acceptable 406
   :proxy-authentication-required 407
   :request-timeout 408
   :conflict 409
   :gone 410
   :length-required 411
   :precondition-failed 412
   :payload-too-large 413
   :uri-too-long 414
   :unsupported-media-type 415
   :range-not-satisfiable 416
   :expectation-failed 417
   :im-a-teapot 418
   :misdirected-request 421
   :unprocessable-content 422
   :unprocessable-entity 422
   :locked 423
   :failed-dependency 424
   :too-early 425
   :upgrade-required 426
   :precondition-required 428
   :too-many-requests 429
   :request-header-fields-too-large 431
   :unavailable-for-legal-reasons 451
   :internal-server-error 500
   :not-implemented 501
   :bad-gateway 502
   :service-unavailable 503
   :gateway-timeout 504
   :http-version-not-supported 505
   :variant-also-negotiates 506
   :insufficient-storage 507
   :loop-detected 508
   :not-extended 510
   :network-authentication-required 511})

(defn- describe-request [req]
  (if-let [method (:request-method req)]
    (str (str/upper-case (name method)) " " (:uri req))
    (str (:uri req))))

(defn- status-data [status req]
  {:status status
   :uri    (:uri req)
   :request-method (:request-method req)})

(defn- unknown-status! [status req]
  (throw (ex-info (str "Unknown status keyword " status
                       " in the response to " (describe-request req)
                       ". Use a number or a key of"
                       " tram.impl.http/status-codes.")
                  (status-data status req))))

(defn- malformed-redirect! [status req issue]
  (throw (ex-info (str "Status vector " (pr-str status)
                       " in the response to " (describe-request req)
                       " " issue)
                  (status-data status req))))

(defn- lower-redirect [response req]
  (let [{[head target params :as status] :status} response
        code (get status-codes head)]
    (cond
      (not (<= 2 (count status) 3))
      (malformed-redirect! status req "takes [status target params?].")

      (nil? code) (unknown-status! head req)
      (not (<= 300 code 399))
      (malformed-redirect!
        status
        req
        (str "must start with a 3xx keyword; " head " is " code "."))

      (keyword? target)
      (-> response
          (assoc
            :status code)
          (assoc-in [:headers "location"]
                    [:tram.html/make target (or params {})]))

      (string? target)
      (if params
        (malformed-redirect! status
                             req
                             "passes params, but a string target takes none.")
        (-> response
            (assoc
              :status code)
            (assoc-in [:headers "location"]
                      target)))

      :else
      (malformed-redirect!
        status
        req
        "needs a route keyword or a string as its target."))))

(defn lower-status
  "Lower a keyword or redirect-vector `:status` to its numeric code.

  A redirect vector `[status target params?]` also sets the `location` header:
  a route keyword target becomes an expandable route ref, a string target
  passes through as the literal location. A numeric or absent status returns
  the response unchanged. Throws on an unknown keyword or a malformed vector,
  naming the request that produced it."
  [response req]
  (let [status (:status response)]
    (cond
      (keyword? status)
      (assoc response
        :status (or (get status-codes status) (unknown-status! status req)))

      (vector? status) (lower-redirect response req)
      :else response)))
