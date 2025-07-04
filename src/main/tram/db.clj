(ns tram.db
  (:require [cheshire.core :as json]
            [next.jdbc.date-time]
            [next.jdbc.prepare :as prepare]
            [next.jdbc.result-set :as rs])
  (:import [org.postgresql.util PGobject]))

(defn pgobj->clj [^org.postgresql.util.PGobject pgobj]
  (let [type  (.getType pgobj)
        value (.getValue pgobj)]
    (case type
      "json"   (json/parse-string value true)
      "jsonb"  (json/parse-string value true)
      "citext" (str value)
      value)))

(extend-protocol rs/ReadableColumn
  java.sql.Array
  (read-column-by-label [^java.sql.Array v _] (vec (.getArray v)))
  (read-column-by-index [^java.sql.Array v _2 _3] (vec (.getArray v)))

  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject pgobj _]
    (pgobj->clj pgobj))
  (read-column-by-index [^org.postgresql.util.PGobject pgobj _2 _3]
    (pgobj->clj pgobj)))

(defn clj->jsonb-pgobj [value]
  (doto (PGobject.)
    (.setType "jsonb")
    (.setValue (json/generate-string value))))

(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [^clojure.lang.IPersistentMap v
                  ^java.sql.PreparedStatement stmt
                  ^long idx]
    (.setObject stmt idx (clj->jsonb-pgobj v)))

  clojure.lang.IPersistentVector
  (set-parameter [^clojure.lang.IPersistentVector v
                  ^java.sql.PreparedStatement stmt
                  ^long idx]
    (let [conn      (.getConnection stmt)
          meta      (.getParameterMetaData stmt)
          type-name (.getParameterTypeName meta idx)]
      (if-let [elem-type (when (= (first type-name) \_)
                           (apply str
                             (rest type-name)))]
        (.setObject stmt idx (.createArrayOf conn String (to-array v)))
        (.setObject stmt idx (clj->jsonb-pgobj v))))))
