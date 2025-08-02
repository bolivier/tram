(ns build
  (:gen-class)
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]))

(def lib
  'com.samples/sample-app)
(def version
  (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir
  "target/classes")
(def basis
  (b/create-basis {:project "deps.edn"}))
(def uber-file
  "target/sample_app.jar")

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs   ["src/main"]
               :target-dir class-dir})
  (b/compile-clj {:basis      basis
                  :ns-compile '[sample-app.core]
                  :class-dir  class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis     basis
           :main      'sample-app.core}))
