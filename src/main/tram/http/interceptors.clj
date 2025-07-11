(ns tram.http.interceptors
  (:require
    [clojure.string :as str]
    [clojure.walk :refer [prewalk]]
    [potemkin :refer [import-vars]]
    [reitit.core :as r]
    [reitit.http.coercion
     :refer
     [coerce-request-interceptor coerce-response-interceptor]]
    [reitit.http.interceptors.exception :refer [exception-interceptor]]
    [reitit.http.interceptors.multipart :refer [multipart-interceptor]]
    [reitit.http.interceptors.muuntaja :as muuntaja]
    [reitit.http.interceptors.parameters :as rhip]
    [reitit.ring]
    [tram.http.lookup :refer [request->template request->template-symbol]]
    [tram.http.route-helpers :refer [expandable-route-ref?]]
    [tram.http.routing :as route]
    [tram.http.utils :as http.utils]
    [tram.http.views :refer [*current-user* *req* *res*]]
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

(def render-template-interceptor
  {:name ::template-renderer
   :leave
   (fn [ctx]
     (if (or (some? (get-in ctx
                            [:response :body]))
             (str/starts-with? (get-in ctx
                                       [:request :uri])
                               "/assets")
             (<= 300
                 (get-in ctx
                         [:response :status])
                 399))
       ctx
       (let [{:keys [request response]} ctx
             context  (:context response)
             template (or (:template response)
                          (request->template request))]
         (if-not template
           (let [route-name      (:tram/route-name request)
                 url             (:uri request)
                 template-symbol (request->template-symbol request)
                 template-name   (name template-symbol)
                 template-ns     (namespace template-symbol)]
             (throw
               (ex-info
                 (str
                   "Route "
                   route-name
                   "("
                   url
                   ") does not have a valid template.

Expected to find template called `"
                   template-name
                   "` at: "
                   template-ns)
                 {:template    template
                  :expected-fn (request->template-symbol (:request ctx))})))
           (binding [*current-user* (:current-user request)
                     *req*          request
                     *res*          response]
             (assoc-in ctx
               [:response :body]
               (template context)))))))})

(def expand-hiccup-interceptor
  "Walks your hiccup tree to find any customizations that need to be expanded.

  By deault, this is only used to expand vectors like:

  [:tram.http.routing/make :route/name route-params] into a string route. This
  applies to keys in headers and the body of the response.

  The keyword is specific there.

  This expands hiccup vectors where the first element is a function.

  TODO: Update this to be extensible."
  {:name ::expand-hiccup-interceptor
   :leave
   (fn [ctx]
     (let [req    (:request ctx)
           router (::r/router req)]
       (when-not router
         (throw (ex-info "Cannot expand hiccup without router in request."
                         {:source ::expand-hiccup-interceptor})))
       (-> ctx
           (update-in
             [:response :body]
             (fn [body]
               (prewalk (fn [node]
                          (cond
                            ;; expand function components into fully
                            ;; realized hiccup
                            (and (vector? node) (fn? (first node)))
                            (let [f    (first node)
                                  args (rest node)]
                              (apply f args))

                            ;; use ::route/make to make a route with the
                            ;; request
                            (expandable-route-ref? node)
                            (let [[_ route-name route-params] node]
                              (route/make-path router route-name route-params))

                            ;; expand keys like `:route/name` into their
                            ;; looked up route name
                            (and (keyword? node) (= "route" (namespace node)))
                            (let [router (:reitit.core/router req)]
                              (:path (reitit.core/match-by-name router node)))

                            :else node))
                        body)))
           (update-in [:response :headers]
                      (fn [headers]
                        (map-vals (fn [header]
                                    (if (expandable-route-ref? header)
                                      (let [[_ route-name route-params] header]
                                        (route/make-path (::r/router req)
                                                         route-name
                                                         route-params))
                                      header))
                                  headers))))))})


(def coercion-interceptors
  [(coerce-request-interceptor)
   (coerce-response-interceptor)
   (rhip/parameters-interceptor)])

(def default-interceptors
  [#_(exception-interceptor)
   inject-route-name
   (multipart-interceptor)
   expand-hiccup-interceptor
   coercion-interceptors
   render-template-interceptor])

(import-vars [muuntaja format-interceptor])
