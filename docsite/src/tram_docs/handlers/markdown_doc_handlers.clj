(ns tram-docs.handlers.markdown-doc-handlers
  (:require [markdown.core :as md]
            [tram.routes :as tr]))


(defn get-static-tram-docs [req]
  {:status 200
   :html   (let [resource-path (str "resources/tram-md-docs/"
                                    (:path (:path-params req))
                                    ".md")]
             (with-out-str (md/md-to-html resource-path *out*)))})

(tr/defroutes routes
  ["/tram/*path"
   {:name :route/static-tram-docs
    :get  get-static-tram-docs}])
