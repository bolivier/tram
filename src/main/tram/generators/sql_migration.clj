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

(def AttributeSchema
  [:map
   [:type :keyword]
   [:name :keyword]
   [:required :boolean]
   [:unique? :boolean]
   [:default :any]
   [:trigger [:literal [:update-updated-at]]]])

(def ActionSchema
  [:map
   [:attributes AttributeSchema]
   [:action-type :keyword] ;; only :create-table for now
  ])

(def MigrationBlueprintSchema
  [:map
   [:model :string]     ;; singular
   [:timestamp :string] ;; "20250628192301"
   [:migration-name :string]
   [:actions [:vector ActionSchema]]])

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

(defn serialize-sql-action [blueprint]
  (-> (hh/create-table (symbol (:table blueprint)))
      (hh/with-columns (mapv serialize-attribute (:attributes blueprint)))))

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
  [_ blueprint]
  (format-sql (first (sql/format
                       {:create-trigger   (keyword (str "set-updated-at-on-"
                                                        (:table blueprint)))
                        :before-update    [:on (:table blueprint)]
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

(defn serialize-to-trigger-sqls
  "Finds any references to triggers that need to be added to the migration file."
  [action]
  (remove nil?
    (concat (map (fn [attr] (render-trigger attr action)) (:attributes action))
            (map (fn [attr] (render-index attr action)) (:attributes action))
            [(render-index (:column action) action)]
            [(render-trigger (:column action) action)])))


(defn serialize-to-down-sql [blueprint]
  (format-sql (first (sql/format (hh/drop-table (symbol (:table blueprint)))))))

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

(defn validate! [blueprint]
  nil)

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

(m/defmulti to-sql-string
  (fn [action] (:type action)))

;; Before going to create the tble assoc in the timestamps if the key is
;; present
(m/defmethod to-sql-string :before
  :create-table
  [action]
  (let [original-attributes (:attributes action)
        attributes (concat [{:name :id
                             :type :primary-key}]
                           original-attributes
                           (when (:timestamps action)
                             [updated-at created-at]))]
    (assoc action :attributes attributes)))

(m/defmethod to-sql-string :create-table
  [action]
  (let [primary-migration
        (format-sql (first (sql/format
                             (-> (hh/create-table (symbol (:table action)))
                                 (hh/with-columns (mapv serialize-attribute
                                                    (:attributes action)))))))

        triggers (serialize-to-trigger-sqls action)]
    (str/join sql-joiner (into [primary-migration] triggers))))

(m/defmethod to-sql-string :add-column
  [action]
  action
  (let [col-data (serialize-attribute (:column action))
        primary  (first (sql/format (-> (apply hh/add-column col-data)
                                        (hh/alter-table (:table action)))))
        triggers (serialize-to-trigger-sqls action)]
    (str/join sql-joiner (into [primary] triggers))))

(m/defmethod to-sql-string :default
  [action]
  (throw (ex-info "Tried to create a sql-string from an invalid type"
                  {:error  :no-matching-method-type
                   :action action})))

(defn write-to-migration-file [blueprint]
  (let [migration-strings (map to-sql-string (:actions blueprint))
        sql-string        (str/join sql-joiner migration-strings)]
    (spit (generate-migration-up-filename blueprint) sql-string)))


(defn write-to-migration-down [blueprint]
  (let [sql-string (map #(serialize-to-down-sql %)
                     (reverse (:actions blueprint)))
        sql-string (str/join sql-joiner sql-string)]
    (spit (generate-migration-down-filename blueprint) sql-string)))

(defn write-to-migration-files [blueprint]
  (validate! blueprint)
  (write-to-migration-down blueprint)
  (write-to-migration-file blueprint)
  nil)
