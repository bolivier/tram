(ns tram.utils.language
  (:require [camel-snake-kebab.core :refer [->snake_case]]
            [clojure.string :as str]
            [declensia.core :as dc]))

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

(defn table-name->foreign-key-id [table-name]
  (str (dc/singularize table-name) "-id"))

(defn foreign-key-id->table-name [fk-id]
  (dc/pluralize (str/replace fk-id #"[-_]id$" "")))

(defn model->filename [model]
  (str (name (dc/singularize model)) ".clj"))
