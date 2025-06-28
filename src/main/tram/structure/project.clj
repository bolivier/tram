(ns tram.structure.project
  "This ns is for any utility code that is for navigation around the project.

  This is not where name conversions go.  For that see `tram.util.language`"
  (:require [tram.core :as tram]
            [tram.utils.language :as lang]))

(defn model-file [model]
  (let [config (tram/get-tram-config)]
    (str "src/main/" (name (:project-name config))
         "/models/"  (lang/model->filename model))))
