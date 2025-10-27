(ns sample-app.concerns.authentication
  (:require [buddy.hashers :as hashers]
            [clojure.string :as str]
            [tram.db :as db]))

(defn hash-password [password]
  (hashers/derive password))

(defn verify-password [password hashed-value]
  (hashers/verify password hashed-value))

(defn correct-password? [hashed-password guess]
  (boolean (when hashed-password
             (verify-password guess
                              hashed-password))))

(defn get-user-password
  "Gets the users password."
  [email]
  (:password (db/select-one "users" :email email)))

(defn get-authenticated-user
  "Returns either the user correctly authenticated by `email` and `password` or
  `nil`."
  [email password-guess]
  (let [user-password (get-user-password email)]
    (if (correct-password? user-password
                           password-guess)
      (db/select-one :models/users
                     :email
                     email)
      nil)))

(defn register-new-account [{:keys [email password]}]
  (db/insert-returning-instance! :models/users
                                 {:email    email
                                  :password password}))

(def cookie-name
  "session-id")

(def cookie-options
  {:path      "/"
   :http-only true
   :secure    (= (System/getenv "ENV") "production") ; Set to false for
                                                     ; development over HTTP
   :same-site :strict
   :max-age   (* 24 60 60)}) ; 24 hours in seconds

(defn parse-cookies
  "Parse Cookie header string into a map"
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

(defn get-cookie-value
  "Extract cookie value from request"
  ([request]
   (get-cookie-value request cookie-name))
  ([request cookie-name]
   (when-let [cookie-header (get-in request [:headers "cookie"])]
     {:session-id (parse-long (get (parse-cookies cookie-header)
                                   cookie-name))})))

(defn cookie-string
  "Generate Set-Cookie header value"
  ([value]
   (cookie-string cookie-name value cookie-options))
  ([cookie-name value options]
   (let [base  (str cookie-name "=" value)
         attrs (cond-> []
                 (:path options)      (conj (str "Path=" (:path options)))
                 (:max-age options)   (conj (str "Max-Age=" (:max-age options)))
                 (:http-only options) (conj "HttpOnly")
                 (:secure options)    (conj "Secure")
                 (:same-site options) (conj (str "SameSite="
                                                 (name (:same-site options)))))]
     (if (seq attrs)
       (str base
            "; "
            (str/join "; "
                      attrs))
       base))))

(defn set-session-cookie [response session-id]
  (assoc-in response
    [:headers "Set-Cookie"]
    (cookie-string cookie-name session-id cookie-options)))

(defn clear-session-cookie [response]
  (assoc-in response
    [:headers "Set-Cookie"]
    (cookie-string cookie-name "" (assoc cookie-options :max-age 0))))

(def authentication-interceptor
  {:name  ::authentication
   :enter (fn [ctx]
            (try
              (let [{:keys [session-id]} (get-cookie-value (:request ctx))
                    {:keys [user-id]} (db/select-one :models/sessions
                                                     session-id)
                    user (db/select-one :models/users user-id)]
                (if user
                  (assoc-in ctx
                    [:request :current-user]
                    user)
                  ctx))
              (catch Exception _
                ;; Could not find user, do not assoc
                ctx)))})
