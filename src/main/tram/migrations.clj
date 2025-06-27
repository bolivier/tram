(ns tram.migrations
  (:require [honey.sql :as sql]
            [honey.sql.helpers :as hh]
            [methodical.core :as m]
            [migratus.core :as migratus]
            [tram.errors :as errors]))

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


(defn generate-migration-up-filename [blueprint])

(defn ^:not-yet-implemented write-to-migration-file [blueprint]
  (let [sql-string (serialize-to-sql blueprint)]
    (spit (generate-migration-up-filename blueprint) sql-string)))

(defn ^:not-yet-implemented delete-migration-file []
  (throw (errors/not-yet-implemented)))

(sql/format (-> (hh/create-table "users")
                (hh/with-columns
                  [[:id :serial [:primary-key]]
                   [:name :text [:not nil]]
                   [:team-id :integer [:references :teams :id]]
                   #_[:email :citext [:not nil] :unique]
                   #_[:cool :text [:default "yes"]]
                   #_[:updated_at :timestamptz [:not nil] [:default :now]]
                   #_[:created_at :timestamptz [:not nil] [:default :now]]
                   [:signup-date :timestamptz [:not nil] [:default [:now]]]])))

(def attr
  {:type :reference
   :name "team-id"})

(def blueprint
  {:model          "user"
   :timestamp      "20250627143956"
   :migration-name "create-model-users"
   :table          "users"
   :attributes     [{:type      :text
                     :required? true
                     :name      "name"}
                    {:type      :citext
                     :unique?   true
                     :required? true
                     :name      "email"}
                    {:type    :text
                     :name    "cool"
                     :default "yes"}
                    {:type    :timestamptz
                     :name    "signup_date"
                     :default :fn/now}]})
