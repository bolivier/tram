(ns tram.migrations
  (:require [honey.sql :as sql]
            [honey.sql.helpers :as hh]
            [methodical.core :as m]
            [tram.core :as tram]))

(m/defmulti serialize-attribute
  (fn [{:keys [name type]}] [name type]))

(m/defmethod serialize-attribute :default
  [attr]
  (let [base [(keyword (:name attr)) (:type attr)]]
    (into base
          (remove nil?)
          [(when (:required? attr)
             [:not nil])
           (when (:unique? attr)
             :unique)
           (when-let [default-value (:default attr)]
             [:default
              (cond
                (and (keyword? default-value)
                     (= "fn" (namespace default-value)))
                [(keyword (name default-value))]

                (keyword? default-value) (keyword (name default-value))
                :else default-value)])])))

(m/defmethod serialize-attribute [:default :reference]
  [attr]
  (conj (serialize-attribute (assoc attr :type :integer))
        [:references :teams :id]))

(defn serialize-blueprint [blueprint]
  (-> (hh/create-table (symbol (:table blueprint)))
      (hh/with-columns (mapv serialize-attribute (:attributes blueprint)))))


(defn serialize-to-sql [blueprint]
  (first (sql/format (serialize-blueprint blueprint))))

(defn serialize-to-down-sql [blueprint]
  (first (sql/format (hh/drop-table (symbol (:table blueprint))))))

(defn generate-migration-filename
  "Generate the path, filename included, for an up migration."
  [direction blueprint]
  (let [{:keys [migration-dir]} (tram/get-migration-config (tram/get-env))
        filename (str (:timestamp blueprint)
                      "-"
                      (:migration-name blueprint)
                      "."
                      (name direction)
                      ".sql")]
    (str "resources/" migration-dir filename)))

(def generate-migration-down-filename
  "Generate the path, filename included, for an up migration."
  (partial generate-migration-filename :down))

(def generate-migration-up-filename
  "Generate the path, filename included, for an up migration."
  (partial generate-migration-filename :up))


(defn write-to-migration-file [blueprint]
  (let [sql-string (serialize-to-sql blueprint)]
    (spit (generate-migration-up-filename blueprint) sql-string)))

(defn write-to-migration-down [blueprint]
  (let [sql-string (serialize-to-down-sql blueprint)]
    (spit (generate-migration-down-filename blueprint) sql-string)))

(defn write-to-migration-files [blueprint]
  (write-to-migration-down blueprint)
  (write-to-migration-file blueprint))
