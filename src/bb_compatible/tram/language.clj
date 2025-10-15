(ns tram.language
  "Language related utilities."
  (:require [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [clojure.string :as str]
            [declensia.core :as dc]))

(defmacro with-same-output
  "TODO move this into a new utils lib

   Executes body with var bound to the string representation of value.
   Keywords are converted to strings (name only) before body execution.
   The result is returned in the same type as the input (string or keyword).
   For keywords, preserves the original namespace."
  [[var value] & body]
  `(let [original-value# ~value
         original-type#  (cond
                           (string? original-value#) :string
                           (keyword? original-value#) :keyword
                           :else
                           (throw (ex-info "Value must be string or keyword"
                                           {:value original-value#})))
         original-ns#    (when (keyword? original-value#)
                           (namespace original-value#))
         string-value#   (if (keyword? original-value#)
                           (name original-value#)
                           original-value#)
         ~var            string-value#
         result#         (do ~@body)]
     (case original-type#
       :string  (str result#)
       :keyword (if original-ns#
                  (keyword original-ns#
                           (str result#))
                  (keyword (str result#))))))

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
  (keyword "models" (dc/pluralize (str/replace (name kw) #"-id$" ""))))

(defn model->filename [model]
  (str (name (dc/singularize model)) ".clj"))

(defn join-table [model-a model-b]
  (let [[first second] (sort [(name model-a) (name model-b)])]
    (keyword (str (dc/singularize first) "-" second))))
