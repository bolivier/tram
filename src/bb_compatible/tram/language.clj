(ns tram.language
  "Language related utilities."
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [declensia.core :as dc]
            [malli.core :as malli]
            [tram.utils :refer [with-same-output]]))

(defn table-name->foreign-key-id [table-name]
  (with-same-output [table table-name]
    (str (dc/singularize table) "-id")))

(defn foreign-key-id->table-name [fk-id]
  (with-same-output [fk fk-id]
    (dc/pluralize (str/replace fk #"[-_]id$" ""))))

(defn as-column
  "Take a kebab-case keyword or string and return a db-safe snake_case string."
  [kw-or-str]
  (csk/->snake_case_string kw-or-str))

(defn index-name
  "Generate the name for an index of `col` on `table`."
  [table col]
  (let [table-name  (csk/->snake_case_string table)
        column-name (as-column col)]
    (str "idx_" table-name "_" column-name)))

(defn modelize
  "Convert a keyword into the same keyword, but representing the model of that
  term.

  By default, pluralizes the name of the keyword. Change `plural?` in opts for
  keywords that are already plural."
  ([kw {:keys [plural?]}]
   (let [kw-no-id (str/replace (name kw) #"-id$" "")]
     (keyword "models"
              (if plural?
                (dc/pluralize kw-no-id)
                kw-no-id))))
  ([kw]
   (modelize kw {:plural? true})))

(defn join-table [model-a model-b]
  (let [[first second] (sort [(name model-a) (name model-b)])]
    (keyword (str first "-" second))))

(def ns-type-lookup
  {:view    "views"
   :handler "handlers"})

(defn convert-ns [a-ns to]
  (when-not (malli/validate [:enum :view :handler]
                            to)
    (throw (ex-info "Tried to convert invalid ns types"
                    {:ns a-ns
                     :to to})))
  (let [segments  (str/split (str a-ns) #"\.")
        converter (fn [segment]
                    (str/replace segment
                                 #"(handlers|views)$"
                                 (ns-type-lookup to)))]
    (str/join "." (map converter segments))))
