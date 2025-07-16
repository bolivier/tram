(ns tram.http.lookup
  (:require [clojure.string :as str]
            [reitit.core :as r]))

(defn handlers-ns->views-ns
  "Given the handler namespace, convert it to the matching view namespace.

  Works on strings or namespaces."
  [handler-ns]
  (let [view-ns-symbol
        (symbol (str/join "."
                          (map (fn [segment]
                                 (-> segment
                                     (str/replace #"-handlers$" "-views")
                                     (str/replace #"^handlers$" "views")))
                            (str/split (str handler-ns) #"\."))))]
    (if-let [view-ns (find-ns view-ns-symbol)]
      view-ns
      (do (require view-ns-symbol)
          (find-ns view-ns-symbol)))))

(defn request->template-symbol
  "Given a request, what symbol represents the default view fn."
  [request]
  (let [router    (::r/router request)
        uri       (:uri request)
        method    (:request-method request)
        match     (r/match-by-path router uri)
        caller-ns (:namespace (:data match))]
    (when caller-ns
      (let [function-name (-> match
                              :data
                              method
                              :handler-var
                              meta
                              :name
                              str
                              (str/replace #"-handler$"
                                           ""))
            template-ns   (str/join "."
                                    (map (fn [segment]
                                           (cond
                                             (= segment "handlers") "views"
                                             (str/ends-with? segment
                                                             "-handlers")
                                             (str/replace segment
                                                          #"-handlers$"
                                                          "-views")

                                             :else segment))
                                      (str/split (str caller-ns)
                                                 #"\.")))]
        (symbol (str template-ns
                     "/"
                     function-name))))))

(defn keyword->template
  "`template-kw` should be a keyword like `:view/my-view` where `my-view` is a
  view function that exists in the view namespace corresponding to the handler
  namespace where this is called."
  [template-kw]
  (requiring-resolve (symbol (str (handlers-ns->views-ns *ns*))
                             (name template-kw))))

(defn request->template [request]
  (-> request
      request->template-symbol
      requiring-resolve))
