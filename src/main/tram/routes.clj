(ns ^:public tram.routes
  "This is part of the public api of Tram.

  Here are fns and vars related to routing."
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clojure.string :as str]
            [clojure.walk :refer [prewalk]]
            [malli.error :as me]
            [malli.util :as mu]
            [muuntaja.core :as muuntaja]
            [potemkin :refer [import-vars]]
            [reitit.core :as r]
            [reitit.http :as http]
            [reitit.http.coercion
             :refer
             [coerce-exceptions-interceptor
              coerce-request-interceptor
              coerce-response-interceptor]]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.multipart :refer [multipart-interceptor]]
            [reitit.http.interceptors.muuntaja :as rhim]
            [reitit.http.interceptors.parameters :as rhip]
            [reitit.ring]
            [tram.html :refer [expanders] :as tram.html]
            [tram.impl.http]
            [tram.impl.router :refer [coerce-route-entries-to-specs map-routes]]
            [tram.logging :as log]
            [tram.rendering.template-renderer :as renderer]
            [tram.utils :refer [map-vals]]
            [tram.vars :refer [*current-user* *req* *res*]]))

(import-vars [tram.impl.http htmx-request? html-request? full-redirect redirect]
             [reitit.http.interceptors.multipart multipart-interceptor]
             [reitit.http.interceptors.parameters parameters-interceptor]
             [reitit.http.coercion
              coerce-exceptions-interceptor
              coerce-request-interceptor
              coerce-response-interceptor]
             [tram.html make-route])

(def expand-hiccup-interceptor
  "Walks your hiccup tree to find any customizations that need to be expanded.

  By deault, this is only used to expand vectors like:

  [:tram.http.routing/make :route/name route-params] into a string route. This
  applies to keys in headers and the body of the response.

  The keyword is specific there.

  This expands hiccup vectors where the first element is a function.

  TODO: Update this to be extensible."
  {:name  ::expand-hiccup-interceptor
   :leave (fn [ctx]
            (let [req      (:request ctx)
                  router   (::r/router req)
                  expander (reduce comp (map #(partial % req) expanders))]
              (when-not router
                (throw (ex-info
                         "Cannot expand hiccup without router in request."
                         {:source ::expand-hiccup-interceptor})))
              (binding [*current-user* (:current-user req)
                        *req*          (:request ctx)
                        *res*          (:response ctx)]
                (-> ctx
                    (update-in [:response :body]
                               (fn [body] (prewalk expander body)))
                    (update-in [:response :headers]
                               (fn [headers] (map-vals expander headers)))))))})

(defn wrap-page-interceptor
  "Wraps the returned html in a full html page (if it should).

  Does nothing if the current request is via htmx, or if it is an assets
  request.

  `full-page-renderer` is the component for your full html page. It should
  render <head> and any other meta tags a full page reload would need for your
  application. It is called with one argument, the contents of the body tag."
  [full-page-renderer]
  {:leave (fn [ctx]
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
  {:name  ::template-renderer
   :leave (fn [ctx]
            (cond
              (or (str/starts-with? (get-in ctx [:request :uri]) "/assets")
                  (<= 300 (get-in ctx [:response :status] 300) 399))
              ctx

              (re-find #"application/json"
                       (get-in ctx [:request :headers "accept"]))
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

(def format-json-body-interceptors
  {:name  ::inject-content-type-interceptors
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

(defn default-error-handler
  "Default error handler for `tram.routes/exception-interceptor`."
  [exception request]
  (log/event! ::uncaught-exception
              {:data {:request   request
                      :exception exception}})
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
     (merge {::default default-error-handler
             :tram.req/coercion
             (fn [e req]
               (let [method (get-in req [:request-method])
                     schema (get (ex-data e) :schema)
                     body   (get (ex-data e) :value)
                     error-handler-fn (get-in req
                                              [::r/match :data method :error]
                                              default-error-handler)]
                 (error-handler-fn schema (assoc req :body body))))}
            config))))

(defn- get-ui-label
  "Default way to generate hiccup for an error.

  Errors from malli are in the form {:key-with-error [\"human-readable-error-message-without-subject\"]

  eg

  {:title [\"should not be empty\"]}

  By default the above error will transalte to \"Title should not be empty\".

  Capitalize the key, swap dashes for spaces, and concat the key with the
  message."
  [schema k err]
  (str (or (:tram.ui/label (second (mu/find schema k)))
           (str/capitalize (name k)))
       " "
       err))

(defn easy-error-handler
  "Easy handling for request coercion errors.

  Add this handler to a route spec, after handler, to the key `:error` in your
  route data to use it. Coercion errors will be caught.

  `handler` is a fn that receives a vector of error message strings based on the
  schema. They use the default format.  It defaults to `clojure.core/identity`.

  `status` is an http status code to use.  It defaults to 400."
  [{:keys [status handler]
    :or   {status  400
           handler identity}}]
  (fn error-handler [schema req]
    (let [body (get-in req [:body])
          coercion-errors (me/humanize (mu/explain-data schema body))
          error-messages (mapcat (fn [[k errs]]
                                   (map (fn [err] (get-ui-label schema k err))
                                     errs))
                           coercion-errors)]
      {:status status
       :body   (handler error-messages)})))

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

(import-vars [rhim format-interceptor])

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
