(ns tram.rendering.template-renderer
  "Render html templates from the ring response."
  (:require [clojure.string :as str]
            [reitit.core :as r]
            [tram.http.lookup :as lookup]
            [tram.http.views :refer [*current-user* *req* *res*]]))

(defprotocol ITemplate
  "Protocol for something that can be used as a render template.

  Only the get-view-fn function is necessary to make this code work, but error
  reporting is much simpler if there is a way to grab the ns and the name also."
  (get-view-fn [this ctx]
    "Get the view fn for this template")
  (get-name [this ctx]
    "Get the name of the template")
  (get-namespace [this ctx]
    "Get the namespace for the template"))

(extend-protocol ITemplate
  clojure.lang.Keyword
  (get-name [this _] (name this))
  (get-namespace [this ctx]
    (str/replace (:namespace (:data (:reitit.core/match (:request ctx))))
                 #"handler"
                 "view"))
  (get-view-fn [this ctx]
    (let [namespace-str (get-namespace this ctx)]
      (requiring-resolve (symbol namespace-str (get-name this ctx)))))

  clojure.lang.Fn
  (get-view-fn [this _] this)
  (get-namespace [this _]
    ;; kindof cheating, but this is mostly for debugging
    (str this))
  (get-name [this _]
    ;; kindof cheating, but this is mostly for debugging
    (str this))

  clojure.lang.Var
  (get-name [this _] (:name (:meta this)))
  (get-namespace [this _] (:namespace (:meta this)))
  (get-view-fn [this _] this)

  nil
  (get-name [_ _] "<nil>")
  (get-namespace [_ ctx]
    (let [request    (:request ctx)
          router     (::r/router request)
          uri        (:uri request)
          match      (r/match-by-path router uri)
          handler-ns (:namespace (:data match))]
      (when handler-ns
        (let [template-ns (lookup/handlers-ns->views-ns handler-ns)]
          template-ns))))
  (get-view-fn [_ ctx]
    (let [request   (:request ctx)
          router    (::r/router request)
          uri       (:uri request)
          method    (:request-method request)
          match     (r/match-by-path router uri)
          caller-ns (:namespace (:data match))]
      (when caller-ns
        (let [function-name (-> (::r/router request)
                                (r/match-by-path uri)
                                :data
                                method
                                :handler-var
                                meta
                                :name
                                str
                                (str/replace #"-handler$"
                                             ""))
              template-ns   (get-namespace nil
                                           ctx)]
          (when function-name
            (requiring-resolve (symbol (str template-ns
                                            "/"
                                            function-name)))))))))

(defn render
  "Renders a template."
  [ctx]
  (let [{:keys [request response]} ctx
        {:keys [locals template]} response
        view-fn (get-view-fn template ctx)]
    (if-not view-fn
      (throw
        (ex-info
          (str
            "Route ("
            (:uri request)
            ") does not have a valid template.

Expected to find template called `"
            (get-name template ctx)
            "` at: "
            (get-namespace template ctx))
          {}))
      (let [layout-fn (apply comp (:layouts ctx))]
        (binding [*current-user* (:current-user request)
                  *req*          request
                  *res*          response]
          (assoc-in ctx [:response :body] (layout-fn (view-fn locals))))))))
