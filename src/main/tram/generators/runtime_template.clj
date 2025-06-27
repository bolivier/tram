(ns tram.generators.runtime-template
  "A runtime template is a template file that gets written to /src/dev/runtime, a
  gitignored directory, for users to have code they are meant to evaluate in the
  repl for side effects, then throw away.

  An example use case is generating a migration. That cli task does not itself
  generate the migration, but instead writes out a runtime template with the
  information in it and some functions that will create the migration. Users can
  then execute those functions repeatedly to modify the migration in a data
  driven way."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [selmer.parser :as selmer]
            [selmer.util]
            [tram.core :as tram]
            [zprint.core :refer [zprint-file-str]]))

(defn get-runtime-filename [blueprint]
  (let [file-prefix "src/dev/runtime/"
        filename    (str "generate_" (:model blueprint) ".clj")]
    (str file-prefix filename)))

(defn get-runtime-ns [blueprint]
  (let [ns-prefix "runtime."
        filename  (str "generate_" (:model blueprint) ".clj")
        namespace (str ns-prefix
                       (-> filename
                           (str/replace ".clj" "")
                           (str/replace "_" "-")))]
    namespace))

(defn get-template [_blueprint]
  "tram/templates/model.template.clj")

(defn format-code [clj-source-string]
  (zprint-file-str clj-source-string ::model-template (tram/get-zprint-config)))

(defn render
  "Render a blueprint into runtime code.

  Returns an unformatted string."
  [blueprint]
  (binding [selmer.util/*escape-variables* false]
    (selmer/render-file (get-template blueprint)
                        {:namespace        (get-runtime-ns blueprint)
                         :blueprint-string blueprint})))

(defn write [blueprint]
  (let [filename (get-runtime-filename blueprint)
        file     (io/file filename)]
    (io/make-parents file)
    (spit file
          (-> blueprint
              render
              format-code))))
