(ns tram.utils.language
  (:require [clojure.string :as str]
            [declensia.core :as dc]))

(defn table-name->foreign-key-id [table-name]
  (str (dc/singularize table-name) "-id"))

(defn foreign-key-id->table-name [fk-id]
  (dc/pluralize (str/replace fk-id #"[-_]id$" "")))
