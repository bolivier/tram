(ns tram.utils.plural
  (:require [clojure.string :refer [ends-with?] :as str]
            [methodical.core :as m]))

(defn in?
  "Return true if x is in coll, else false."
  [x coll]
  ;; FIXME: duplicate
  (some #(= x %) coll))

(defn rule [id regexp replacement]
  {:id          id
   :type        :rule
   :regexp      regexp
   :replacement replacement})

(def ^:dynamic *pluralize-rules*
  (list
    (rule ::base #"$" "s")
    ;; (rule /^(ax|test)is$/i, '\1es')
    ;; inflect.plural(/(octop|vir)us$/i, '\1i')
    ;; inflect.plural(/(octop|vir)i$/i, '\1i')
    ;; inflect.plural(/(alias|status)$/i, '\1es')
    ;; inflect.plural(/(bu)s$/i, '\1ses')
    ;; inflect.plural(/(buffal|tomat)o$/i, '\1oes')
    ;; inflect.plural(/([ti])um$/i, '\1a')
    ;; inflect.plural(/([ti])a$/i, '\1a')
    ;; inflect.plural(/sis$/i, "ses")
    ;; inflect.plural(/(?:([^f])fe|([lr])f)$/i, '\1\2ves')
    ;; inflect.plural(/(hive)$/i, '\1s')
    ;; inflect.plural(/([^aeiouy]|qu)y$/i, '\1ies')
    ;; inflect.plural(/(x|ch|ss|sh)$/i, '\1es')
    ;; inflect.plural(/(matr|vert|ind)(?:ix|ex)$/i, '\1ices')
    ;; inflect.plural(/^(m|l)ouse$/i, '\1ice')
    ;; inflect.plural(/^(m|l)ice$/i, '\1ice')
    ;; inflect.plural(/^(ox)$/i, '\1en')
    ;; inflect.plural(/^(oxen)$/i, '\1')
    ;; inflect.plural(/(quiz)$/i, '\1zes')
  ))

(def ^:dynamic *singularize-rules*
  (list (rule ::base #"s$" "")))

(m/defmulti pluralize
  "Return the pluralized noun."
  identity)

(m/defmethod pluralize :default
  [word]
  (loop [rules *pluralize-rules*]
    (let [rule (first rules)]
      (when rule
        (if (re-find (:regexp rule)
                     word)
          (str/replace word
                       (:regexp rule)
                       (:replacement rule))
          (recur (rest rules)))))))

(m/defmulti singularize
  "Returns the singularized noun."
  :none)

(m/defmethod singularize :default
  [word]
  (str/replace word #"s$" ""))

