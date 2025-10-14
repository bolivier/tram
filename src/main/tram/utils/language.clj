(ns tram.utils.language
  (:require [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [clojure.string :as str]
            [declensia.core :as dc]
            [tram.utils.core :refer [with-same-output]]))

(defn lower-case?
  "Returns `s` if `s` consists of no capital letters [A-Z].

  Returns `nil` otherwise

  Other non alphanumeric chars are allowd."
  [s]
  (re-matches #"[^A-Z]*" s))

(defn snake-case?
  "Returns `s` if `s` is a snake case variable."
  [s]
  (= (->snake_case s) s))

(defn kebab-case? [s]
  (= (->kebab-case s) s))

(defn table-name->foreign-key-id [table-name]
  (with-same-output [table table-name]
    (str (dc/singularize table) "-id")))

(defn foreign-key-id->table-name [fk-id]
  (with-same-output [fk fk-id]
    (dc/pluralize (str/replace fk #"[-_]id$" ""))))

(defn modelize [kw]
  (keyword "model" (name kw)))

(defn model->filename [model]
  (str (name (dc/singularize model)) ".clj"))
