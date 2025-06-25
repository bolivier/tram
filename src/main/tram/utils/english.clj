(ns tram.utils.english
  (:require [clj-commons.humanize.inflect :as hi]))

(defn pluralize [s]
  (hi/pluralize-noun 2 s))
