(ns ^:public tram.csrf
  "CSRF protection using signed double-submit cookie pattern.

  Generates a random token, signs it with buddy-sign (JWS), and stores it in a
  cookie. The plaintext token is rendered into HTML via hidden fields or meta
  tags. On state-changing requests, the submitted token is compared against the
  signed cookie's token."
  (:require [buddy.core.codecs :as codecs]
            [buddy.core.nonce :as nonce]
            [buddy.sign.jwt :as jwt]
            [clojure.string :as str]
            [tram.vars :refer [*req*]]))

(defn generate-token
  "Generate a random 32-byte hex string for use as a CSRF token."
  []
  (codecs/bytes->hex (nonce/random-bytes 32)))

(defn sign-token
  "Sign a CSRF token with the given secret using JWT."
  [secret token]
  (jwt/sign {:token token} secret))

(defn unsign-token
  "Unsign a CSRF token. Returns the token string on success, nil on failure."
  [secret signed]
  (try
    (:token (jwt/unsign signed secret))
    (catch Exception _
      nil)))

(def ^:private state-changing-methods
  #{:post :put :patch :delete})

(def ^:private cookie-name
  "__csrf-token")
(def ^:private form-param
  "__anti-forgery-token")
(def ^:private header-name
  "x-csrf-token")

(defn- parse-cookies
  "Parse Cookie header string into a map."
  [cookie-header]
  (when cookie-header
    (into {}
          (comp (map str/trim)
                (map #(str/split %
                                 #"="
                                 2))
                (filter #(= 2 (count %))))
          (str/split cookie-header
                     #";"))))

(defn- get-csrf-cookie
  "Extract the signed CSRF token from request cookies."
  [request]
  (some-> (get-in request [:headers "cookie"])
          parse-cookies
          (get cookie-name)))

(defn- get-submitted-token
  "Get the CSRF token submitted via form param or header."
  [request]
  (or (get-in request [:form-params form-param])
      (get-in request [:body-params (keyword form-param)])
      (get-in request [:headers header-name])))

(defn- csrf-disabled?
  "Check if CSRF is disabled for this route via {:csrf false} in route data."
  [request]
  (some-> request
          :reitit.core/match :data
          :csrf false?))

(defn- cookie-string
  "Build a Set-Cookie header value for the CSRF cookie."
  [signed-token]
  (str cookie-name "=" signed-token "; Path=/; HttpOnly; SameSite=Strict"))

(defn- append-set-cookie
  "Append a Set-Cookie value, preserving any existing Set-Cookie headers."
  [response cookie-value]
  (update-in response
             [:headers "Set-Cookie"]
             (fn [existing]
               (cond
                 (nil? existing)    cookie-value
                 (string? existing) [existing cookie-value]
                 (vector? existing) (conj existing cookie-value)
                 :else              [existing cookie-value]))))

(defn csrf-interceptor
  "Returns a CSRF protection interceptor using signed double-submit cookie.

  `secret` should be a stable secret key for signing tokens.

  On enter:
  - Reads the signed CSRF cookie and unsigns it (or generates a fresh token)
  - Assocs the plaintext token at [:request :csrf-token]
  - For state-changing requests (POST/PUT/PATCH/DELETE), validates the submitted
    token matches the cookie token. Returns 403 on mismatch.
  - Routes with {:csrf false} in route data skip validation.

  On leave:
  - Signs the token and sets the CSRF cookie via Set-Cookie header."
  [secret]
  {:name  ::csrf
   :enter (fn [ctx]
            (let [request       (:request ctx)
                  signed-cookie (get-csrf-cookie request)
                  cookie-token  (when signed-cookie
                                  (unsign-token secret
                                                signed-cookie))
                  token         (or cookie-token (generate-token))
                  request       (assoc request :csrf-token token)
                  ctx           (assoc ctx :request request)]
              (if (and (state-changing-methods (:request-method request))
                       (not (csrf-disabled? request)))
                (let [submitted (get-submitted-token request)]
                  (if (= token submitted)
                    ctx
                    (assoc ctx
                      :response {:status 403
                                 :body   "Invalid CSRF token"}
                      :queue    [])))
                ctx)))
   :leave (fn [ctx]
            (let [token        (get-in ctx [:request :csrf-token])
                  signed-token (sign-token secret token)]
              (update ctx
                      :response
                      append-set-cookie
                      (cookie-string signed-token))))})

(defn csrf-hidden-field
  "Returns a hiccup hidden input element containing the CSRF token.

  Must be called within the context of a request (where `*req*` is bound)."
  []
  [:input {:type  "hidden"
           :name  form-param
           :value (:csrf-token *req*)}])

(defn csrf-meta-tag
  "Returns a hiccup meta element containing the CSRF token.

  Useful for JavaScript frameworks that need to read the token from the DOM.
  Must be called within the context of a request (where `*req*` is bound)."
  []
  [:meta {:name    "csrf-token"
          :content (:csrf-token *req*)}])
