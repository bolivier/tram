(ns tram.generators.sql-migration
  "This namespace is for writing migration blueprints into migration files."
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [honey.sql :as sql]
            [honey.sql.helpers :as hh]
            [methodical.core :as m]
            [migratus.core]
            [tram.language :as lang]
            [tram.tram-config :as tram.config])
  (:import (com.github.vertical_blank.sqlformatter SqlFormatter)))

(defn format-sql [sql]
  (SqlFormatter/format sql))

(m/defmulti serialize-attribute
  "Returns a version of the attribute as a vector ready to go to a honeysql
  helper."
  (fn [{:keys [name type]}] [name type]))

(m/defmethod serialize-attribute [:id :primary-key]
  [_]
  [:id :serial :primary :key])

(m/defmethod serialize-attribute :default
  [attr]
  (let [base [(keyword (:name attr)) (:type attr)]]
    (into base
          (remove nil?)
          [(when (get attr
                      :required?
                      true)
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
        [:references
         (or (:table-name attr) (lang/foreign-key-id->table-name (:name attr)))
         :id]))

(sql/register-fn! :on
                  (fn on-formatter [f args]
                    [(str "ON " (csk/->snake_case_string (first args)))]))

(defn generic-formatter [clause x]
  (let [[sql & params] (if (or (vector? x)
                               (ident? x))
                         (sql/format-expr x)
                         (sql/format-dsl x))]
    (into [(str (sql/sql-kw clause) " " sql)] params)))

(sql/register-clause!
  :execute-function
  (fn [clause x]
    [(str (sql/sql-kw clause) " " (csk/->snake_case_string x) "()")])
  nil)
(sql/register-clause! :for-each #'generic-formatter :execute-function)

(sql/register-clause! :before-update
                      (fn [clause x]
                        (let [[sql & params] (if (or (vector? x)
                                                     (ident? x))
                                               (sql/format-expr x)
                                               (sql/format-dsl x))]
                          (into [(str "BEFORE UPDATE " sql)] params)))
                      :for-each)

(sql/register-clause! :create-trigger #'generic-formatter :before-update)

(defmulti render-trigger
  (fn [attr _] (:trigger attr)))

(defmethod render-trigger :update-updated-at
  [_ action]
  (format-sql (first (sql/format
                       {:create-trigger   (keyword (str "set-updated-at-on-"
                                                        (:table action)))
                        :before-update    [:on (:table action)]
                        :for-each         :row
                        :execute-function :update-updated-at-column}))))

(defmethod render-trigger :default
  [_ _]
  nil)

(defn render-index [attr action]
  (when (:index? attr)
    (let [col-name (csk/->snake_case_string (:name attr))]
      (str "CREATE INDEX "
           (lang/index-name (:table action)
                            (:name attr))
           " ON "
           (:table action)
           "("
           (lang/as-column col-name)
           ")"))))

(defn render-down-trigger [attr action]
  (when (:trigger attr)
    (let [trigger-name (csk/->snake_case_string (:trigger attr))
          table-name   (name (:table action))]
      (str "DROP TRIGGER " trigger-name
           " ON " table-name))))

(defn render-down-index [attr action]
  (when (:index? attr)
    (str "DROP INDEX "
         (lang/index-name (:table action)
                          (:name attr)))))

(defn serialize-to-extra-statements
  "Creates extra sql statements for indexes and triggers."
  [action]
  (remove nil?
    (let [attrs    (conj (:attributes action) (:column action))
          triggers (map #(render-trigger % action) attrs)
          indexes  (map #(render-index % action) attrs)]
      (concat triggers indexes))))

(defn serialize-to-extra-down-statements
  "Creates down sql for indexes and triggers."
  [action]
  (remove nil?
    (let [attrs    (conj (:attributes action) (:column action))
          triggers (map #(render-down-trigger % action) attrs)
          indexes  (map #(render-down-index % action) attrs)]
      (concat triggers indexes))))

(m/defmulti to-down-sql-string
  :type)

(m/defmethod to-down-sql-string :default
  [action]
  (throw (ex-info "Tried to generate down sql for unsupported type"
                  {:action action
                   :possible-solution (str "Implement `to-down-sql-string` for "
                                           (:type action))})))

(m/defmethod to-down-sql-string :create-table
  [action]
  (first (sql/format (hh/drop-table (symbol (:table action))))))

(m/defmethod to-down-sql-string :add-column
  [action]
  (first (sql/format {:drop-column [(:name (:column action))]
                      :alter-table (:table action)})))

(defn generate-migration-filename
  "Generate the path, filename included, for an up migration."
  [direction blueprint]
  (let [{:keys [migration-dir]} (tram.config/get-migration-config
                                  (tram.config/get-env))
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

(def updated-at
  {:name      :updated-at
   :type      :timestamptz
   :required? true
   :default   :fn/now
   :trigger   :update-updated-at})

(def created-at
  {:name      :created-at
   :type      :timestamptz
   :required? true
   :default   :fn/now})

(def sql-joiner
  "\n\n--;;\n\n")

(m/defmulti to-up-sql-string
  :type)

;; Before going to create the tble assoc in the timestamps if the key is
;; present
(m/defmethod to-up-sql-string :before
  :create-table
  [action]
  (let [original-attributes (:attributes action)
        attributes (concat [{:name :id
                             :type :primary-key}]
                           original-attributes
                           (when (:timestamps action)
                             [updated-at created-at]))]
    (assoc action :attributes attributes)))

(m/defmethod to-up-sql-string :create-table
  [action]
  (let [primary-migration
        (format-sql (first (sql/format
                             (-> (hh/create-table (symbol (:table action)))
                                 (hh/with-columns (mapv serialize-attribute
                                                    (:attributes action)))))))

        triggers (serialize-to-extra-statements action)]
    (str/join sql-joiner (into [primary-migration] triggers))))

(m/defmethod to-up-sql-string :add-column
  [action]
  action
  (let [col-data (serialize-attribute (:column action))
        primary  (first (sql/format (-> (apply hh/add-column col-data)
                                        (hh/alter-table (:table action)))))
        triggers (serialize-to-extra-statements action)]
    (str/join sql-joiner (into [primary] triggers))))

(m/defmethod to-up-sql-string :default
  [action]
  (throw (ex-info "Tried to create a sql-string from an invalid type"
                  {:error  :no-matching-method-type
                   :action action})))

(defn write-to-migration-up [blueprint]
  (let [migration-strings (map to-up-sql-string (:actions blueprint))
        sql-string        (str/join sql-joiner migration-strings)]
    (spit (generate-migration-up-filename blueprint) sql-string)))


(defn write-to-migration-down [blueprint]
  (let [actions    (reverse (:actions blueprint))
        sql-string (map to-down-sql-string actions)
        sql-string (str/join sql-joiner sql-string)]
    (spit (generate-migration-down-filename blueprint) sql-string)))

(defn write-to-migration-files [blueprint]
  (write-to-migration-down blueprint)
  (write-to-migration-up blueprint)
  nil)
