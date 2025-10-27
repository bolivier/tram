(ns build
  (:gen-class)
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]))

(def lib
  'org.clojars.bolivier/tram)

(def version
  "0.0.1")

(def class-dir
  "target/classes")

(def basis
  (b/create-basis {:project "deps.edn"}))

(def uber-file
  "target/tram.jar")

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (clean nil)
  (b/write-pom {:class-dir class-dir
                :lib       lib
                :version   version
                :basis     basis
                :src-dirs  ["src/main" "src/bb_compatible"]})
  (b/copy-dir {:src-dirs   ["src/main" "src/bb_compatible"]
               :target-dir class-dir})
  (b/compile-clj {:basis      basis
                  :ns-compile '[tram.core]
                  :class-dir  class-dir})
  (b/jar {:class-dir class-dir
          :jar-file  uber-file
          :pom-file  (b/pom-path {:lib       lib
                                  :class-dir class-dir})}))
