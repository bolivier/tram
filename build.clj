(ns build
  (:gen-class)
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]))

(def lib
  'org.clojars.bolivier/tram)

;; This version is what consumers (the starter template) request, so the
;; locally built and the published artifact resolve to the same coordinate.
(def version
  "0.2.0-SNAPSHOT")

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
  ;; Do not AOT-compile: this is a library jar consumed via deps.edn, and
  ;; `compile-clj` on `tram.core` transitively dumps compiled classes for
  ;; third-party namespaces (potemkin, clj-tuple, sci, ...) into the jar.
  ;; Those stale AOT artifacts then shadow each consumer's own versions and
  ;; break with errors like
  ;; `ClassNotFoundException: clojure.lang.PersistentUnrolledVector`.
  (b/jar {:class-dir class-dir
          :jar-file  uber-file
          :pom-file  (b/pom-path {:lib       lib
                                  :class-dir class-dir})}))
