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
