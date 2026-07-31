(ns rhizome.utils
  (:require [clojure.string :as str]))

(defn kw->string [kw]
  (cond
    (simple-keyword? kw) (name kw)
    (qualified-keyword? kw)
    (str (str/replace (namespace kw) "." "_") "___" (name kw))

    :else kw))
