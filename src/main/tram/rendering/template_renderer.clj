(ns tram.rendering.template-renderer
  "Rendering html templates from the ring response."
  (:require
    [clojure.string :as str]
    [tram.http.lookup :refer [request->template request->template-symbol]]
    [tram.http.views :refer [*current-user* *req* *res*]]))

(defn render [ctx]
  (let [{:keys [request response]} ctx
        context  (:context response)
        template (or (:template response) (request->template request))]
    (if-not template
      (let [url           (:uri request)
            template-symbol (request->template-symbol request)
            template-name (name template-symbol)
            template-ns   (namespace template-symbol)]
        (throw
          (ex-info
            (str
              "Route ("
              url
              ") does not have a valid template.

Expected to find template called `"
              template-name
              "` at: "
              template-ns)
            {:template    template
             :expected-fn (request->template-symbol (:request ctx))})))
      (let [layout-fn (apply comp (:layouts ctx))]
        (binding [*current-user* (:current-user request)
                  *req*          request
                  *res*          response]
          (assoc-in ctx
            [:response :body]
            (let [template (if (keyword? template)
                             (let [namespace-ns (str/replace
                                                  (:namespace
                                                    (:data (:reitit.core/match
                                                             request)))
                                                  #"handler"
                                                  "view")]
                               (requiring-resolve (symbol namespace-ns
                                                          (name template))))
                             template)]
              (layout-fn (template context)))))))))
