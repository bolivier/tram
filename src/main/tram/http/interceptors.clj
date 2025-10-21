(ns tram.http.interceptors
  (:require [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [clojure.string :as str]
            [clojure.walk :refer [prewalk]]
            [potemkin :refer [import-vars]]
            [reitit.core :as r]
            [reitit.http.coercion
             :refer
             [coerce-exceptions-interceptor
              coerce-request-interceptor
              coerce-response-interceptor]]
            [reitit.http.interceptors.exception :as exception]
            [reitit.http.interceptors.multipart :refer [multipart-interceptor]]
            [reitit.http.interceptors.muuntaja :as muuntaja]
            [reitit.http.interceptors.parameters :as rhip]
            [reitit.ring]
            [taoensso.telemere :as t]
            [tram.http.route-helpers :refer [expandable-route-ref?]]
            [tram.http.routing :as route]
            [tram.http.utils :as http.utils]
            [tram.http.views :refer [*current-user* *req* *res*]]
            [tram.rendering.template-renderer :as renderer]
            [tram.utils :refer [map-vals]]))

(def inject-route-name
  "Injects the name of the current route under the request key `:route-name`"
  {:name  :inject-route-name
   :enter (fn [ctx]
            (let [router     (get-in ctx [:request :reitit.core/router])
                  path       (get-in ctx [:request :reitit.core/match :path])
                  route-name (:name (:data (r/match-by-path router path)))]
              (assoc ctx :route-name route-name)))})

(defn default-as-page-renderer
  ([body]
   (default-as-page-renderer "Tram Template App" body))
  ([title body]
   [:html
    [:head
     [:title title]
     [:meta
      {:name "htmx-config"
       :content
       "{
        \"responseHandling\":[
            {\"code\":\"204\", \"swap\": false},
            {\"code\":\"[23]..\", \"swap\": true},
            {\"code\":\"422\", \"swap\": true},
            {\"code\":\"[45]..\", \"swap\": true, \"error\":true},
            {\"code\":\"...\", \"swap\": true}
        ]
    }"}]
     [:script
      {:src "https://unpkg.com/htmx.org@2.0.4"
       :integrity
       "sha384-HGfztofotfshcF7+8n44JQL2oJmowVChPTg48S+jvZoztPfvwD79OC/LTtG6dMp+"

       :crossorigin "anonymous"}]
     [:link {:rel  :stylesheet
             :href "/assets/index.css"}]
     [:script {:src         "https://unpkg.com/htmx-ext-response-targets@2.0.2"
               :crossorigin "anonymous"}]]
    [:body body]]))

(defn as-page-interceptor
  "Checks if the current page w requested via htmx. If not, wrap the response body
  with html for a full page.

  `full-page-renderer` is the component for your full html page. This function
  should render <head> and any other meta tags a full page reload would need for
  your application."
  [full-page-renderer]
  {:leave (fn [ctx]
            (let [req       (:request ctx)
                  html?     (http.utils/html-request? req)
                  htmx?     (http.utils/htmx-request? req)
                  resource? (str/starts-with? (:uri req) "/assets")
                  needs-full-page? (and html? (not htmx?) (not resource?))]
              (update-in ctx
                         [:response :body]
                         (fn [body]
                           (cond
                             (nil? body) ""
                             needs-full-page?
                             (let [f (or full-page-renderer
                                         default-as-page-renderer)]
                               (f body))

                             :else body)))))})

;; TODO make this more robust and decomplect concepts
(def render-template-interceptor
  {:name  ::template-renderer
   :leave (fn [ctx]
            (cond
              (or (str/starts-with? (get-in ctx [:request :uri]) "/assets")
                  (<= 300 (get-in ctx [:response :status]) 399))
              ctx

              (re-find #"application/json"
                       (get-in ctx [:request :headers "accept"]))
              (update ctx
                      :response
                      (fn [res]
                        (assoc res
                          :body (:data res))))

              :else (renderer/render ctx)))})

(defn hiccup-component-expander [req node]
  (if (and (vector? node)
           (fn? (first node)))
    (let [f    (first node)
          args (rest node)]
      (apply f
        args))
    node))

(defn route-name-expander [req node]
  (let [router (:reitit.core/router req)]
    (cond
      (expandable-route-ref? node)
      (let [[_ route-name route-params] node]
        (route/make-path router route-name route-params))

      (and (keyword? node) (= "route" (namespace node)))
      (route/make-path router node nil)

      :else node)))

(def expanders
  "List of functions that take a req and a node and return an expanded node, for
  whatever expanded means."
  [hiccup-component-expander route-name-expander])

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

(def coercion-interceptors
  [(coerce-request-interceptor)
   (coerce-response-interceptor)
   (coerce-exceptions-interceptor)
   (rhip/parameters-interceptor)])

(def default-interceptors
  [inject-route-name
   (multipart-interceptor)
   expand-hiccup-interceptor
   format-json-body-interceptors
   coercion-interceptors
   render-template-interceptor])

(defn default-error-handler [message exception request]
  (t/event! ::uncaught-exception
            {:data {:request   request
                    :exception exception
                    :message   message}})
  {:status 500
   :body   "An unknown error occurred"})


;; This is to support catching reitit coercion requests with a tram keyword.
;; It's not strictly necessary, but you would have to update the usage of the
;; keyword below in the exception interceptor.
(derive :tram.req/coercion :reitit.coercion/request-coercion)

(defn exception-interceptor
  ([]
   (exception-interceptor {}))
  ([config]
   (exception/exception-interceptor
     (merge {:default (partial default-error-handler "error")
             :tram.req/coercion
             (fn [e req]
               (let [method (get-in req [:request-method])
                     schema (get (ex-data e) :schema)
                     body   (get (ex-data e) :value)
                     error-handler-fn
                     (get-in req [:reitit.core/match :data method :error])]
                 (prn 'output (error-handler-fn schema (assoc req :body body)))
                 (error-handler-fn schema (assoc req :body body))))}
            config))))

(import-vars [muuntaja format-interceptor])

(defn layout-interceptor [layout-fn]
  {:name  ::layout-interceptor
   :enter (fn [ctx]
            (update ctx
                    :layouts
                    (fn [layouts] (conj (or layouts []) layout-fn))))})
