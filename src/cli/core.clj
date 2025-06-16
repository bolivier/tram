#! /usr/bin/env bb
(ns cli.core)

(defn -main
  "This is a docstring"
  [& args]
  (println "Running as main with args:" args))

(when (= *file* (System/getProperty "babashka.file"))
  (-main *command-line-args*))
