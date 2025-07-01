(ns tram.http.interceptors
  (:require [camel-snake-kebab.core :refer [->kebab-case]]
            [clojure.string :as str]
            [clojure.walk :refer [prewalk]]
            [reitit.core :as r]
            [tram.http.routing :as route]
            [tram.http.utils :as http.utils]
            [tram.utils :refer [map-vals]]))

(def inject-route-name
  "Injects the name of the current route under the request key `:route-name`"
  {::name inject-route-name
   :enter (fn [ctx]
            (let [router     (get-in ctx [:request :reitit.core/router])
                  path       (get-in ctx [:request :reitit.core/match :path])
                  route-name (:name (:data (r/match-by-path router path)))]
              (assoc ctx :route-name route-name)))})

(defn as-page-interceptor
  "Checks if the current page w requested via htmx. If not, wrap the response body
  with html for a full page.

  `full-page-renderer` is the component for your full html page. This function
  should render <head> and any other meta tags a full page reload would need for
  your application."
  [full-page-renderer]
  {:leave (fn [ctx]
            (let [req   (:request ctx)
                  html? (http.utils/html-request? req)
                  htmx? (http.utils/htmx-request? req)
                  needs-full-page? (and html? (not htmx?))]
              (update-in ctx
                         [:response :body]
                         (if needs-full-page?
                           full-page-renderer
                           identity))))})

(defn template->namespace [template]
  (->kebab-case (str (name (:project/name (tram.core/get-tram-config)))
                     ".views."
                     (str/join "."
                               (rest (str/split (namespace template) #"\.")))
                     "-views")))

(defn template->view-fn-name [template]
  (->kebab-case (last (str/split (name template) #"\."))))

(def render-template-interceptor
  {:name  ::template-renderer
   :leave (fn [ctx]
            (def ctx
              ctx)
            (let [context             (get-in ctx [:response :context])
                  template            (get-in ctx [:response :template])
                  view-namespace      (template->namespace template)
                  view-fn-name        (template->view-fn-name template)
                  qualified-fn-symbol (symbol
                                        (str view-namespace "/" view-fn-name))
                  view-fn             (requiring-resolve qualified-fn-symbol)]
              (if-not view-fn
                (throw (ex-info (str "Searched for render function in "
                                     namespace
                                     "/"
                                     view-fn-name
                                     " but could not find it.")
                                {:template     template
                                 :view-fn-name view-fn-name
                                 :view-ns      namespace}))
                (assoc-in ctx [:response :body] (apply view-fn context)))))})

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
                            (and (vector? node) (= ::route/make (first node)))
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
                                    (if (and (vector? header)
                                             (= ::route/make (first header)))
                                      (let [[_ route-name route-params] header]
                                        (route/make-path (::r/router req)
                                                         route-name
                                                         route-params))
                                      header))
                                  headers))))))})
