(ns tram.generators.runtime-template
  "A runtime template is a template file that gets written to /src/dev/runtime, a
  gitignored directory, for users to have code they are meant to evaluate in the
  repl for side effects, then throw away.

  An example use case is generating a migration. That cli task does not itself
  generate the migration, but instead writes out a runtime template with the
  information in it and some functions that will create the migration. Users can
  then execute those functions repeatedly to modify the migration in a data
  driven way."
  (:require [camel-snake-kebab.core :refer [->snake_case_string]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [selmer.parser :as selmer]
            [selmer.util]
            [tram.core :as tram]
            [zprint.core :refer [zprint-file-str]]))

(def runtime-defaults
  {:root "runtimes"
   :path "dev/runtimes/"})

(defn get-runtime-filename [blueprint]
  (let [filename
        (str "generate_" (->snake_case_string (:model blueprint)) ".clj")]
    (str (:path runtime-defaults) filename)))

(defn get-runtime-ns [blueprint]
  (let [filename  (str "generate_" (name (:model blueprint)) ".clj")
        namespace (str (:root runtime-defaults)
                       "."
                       (-> filename
                           (str/replace ".clj" "")
                           (str/replace "_" "-")))]
    namespace))

(defn get-template [_blueprint]
  "tram/templates/model.clj.template")

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
