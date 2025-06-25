(ns tram.migrations
  (:require [tram.errors :as errors]))

(defn ^:not-yet-implemented serialize-to-sql [blueprint]
  (throw (errors/not-yet-implemented)))

(defn ^:not-yet-implemented write-to-migration-file [sql-string]
  (throw (errors/not-yet-implemented)))

(defn ^:not-yet-implemented delete-migration-file []
  (throw (errors/not-yet-implemented)))
