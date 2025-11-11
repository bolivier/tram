(ns tram.language
  "Language related utilities."
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [declensia.core :as dc]
            [malli.core :as malli]
            [tram.utils :refer [with-same-output]]))

;;;;;;;;;;;;;;;;;;;;;;;;;
;; databaes lang utils ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

(defn model->foreign-key
  "Converts a model keyword, `:models/users` to a foreign key, `:user-id`."
  [s]
  (keyword (str (dc/singularize (name s)) "-id")))

(defn name->foreign-key
  "Converts a named string to a foreign-key.  Intended for singular words.

  Does not modify input."
  [s]
  (keyword (str (name s) "-id")))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;
;; namespace lang utils ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ns-ize
  "Takes a string and converts file-y characters to their ns counterparts.

  Replaces slashes with dots, and underscores with dashes.

  See also: `tram.language/file-ize`"
  [s]
  (-> s
      (str/replace "/" ".")
      (str/replace "_" "-")))

(defn file-ize
  "Takes a string and converts ns-y characters to their file counterparts.

  Replaces dots with slashes, and dashes with underscores.

  See also: `tram.language/ns-ize`"
  [s]
  (-> s
      (str/replace "." "/")
      (str/replace "-" "_")))

(defn ns->filename
  "Takes a `ns` either as a namespace or a string and converts it to a string representation of a filename.

  Converts dots to slashes.  Appends .clj"
  ([nsp1 & nsps]
   (ns->filename (str/join "." (concat [nsp1] nsps))))
  ([ns]
   (-> ns
       str
       file-ize
       (str ".clj"))))

(defn filename->ns
  "Takes a string filename and converts it to a string representation of a namespace with `tram.language/file-ize`.

  Has a multi-arity version that will join filename partials with \"/\" before calling back to itself.

  Removes .clj suffix."
  ([fnp1 & fnps]
   (filename->ns (str/join "/" (concat [fnp1] fnps))))
  ([filename]
   (-> filename
       str
       ns-ize
       (str/replace #"\.clj$" ""))))

(defn convert-ns [a-ns to]
  (when-not (malli/validate [:enum :view :handler]
                            to)
    (throw (ex-info "Tried to convert invalid ns types"
                    {:ns a-ns
                     :to to})))
  (let [ns-type-lookup {:view    "views"
                        :handler "handlers"}
        segments       (str/split (str a-ns) #"\.")
        converter      (fn [segment]
                         (str/replace segment
                                      #"(handlers|views)$"
                                      (ns-type-lookup to)))]
    (str/join "." (map converter segments))))

;;;;;;;;;;;;;;;;;;;;;;;;
;; numeric lang utils ;;
;;;;;;;;;;;;;;;;;;;;;;;;

(defn ordinal
  "Gets the ordinal suffix of a number, `n`"
  [n]
  (let [one     (mod n 10)
        hundred (mod n 100)]
    (cond
      (and (= 1 one) (not= 11 hundred)) "st"
      (and (= 2 one) (not= 12 hundred)) "nd"
      (and (= 3 one) (not= 13 hundred)) "rd"
      :else                             "th")))

(defn with-ordinal
  "Convert number `n` to a string and append the result of
  `tram.language/ordinal`"
  [n]
  (str n (ordinal n)))
