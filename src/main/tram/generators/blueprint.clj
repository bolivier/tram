(ns tram.generators.blueprint
  (:require [camel-snake-kebab.core :refer [->snake_case]]
            [clojure.string :as str]
            [declensia.core :as dc]
            [malli.core :as m]
            [selmer.util]
            [tram.utils.language :as lang]
            [tram.utils.time :as time]))

(def PostgresType
  [:enum
   :string
   :text
   :citext
   :integer
   :bigint
   :smallint
   :decimal
   :float
   :double
   :boolean
   :date
   :datetime
   :timestamp
   :timestamptz
   :uuid
   :json
   :jsonb
   :bytea
   :inet
   :cidr])

(def type-aliases
  {:int :integer})

(defn coerce-to-type
  "Some types have aliases"
  [t]
  (let [safe-type (get type-aliases t t)]
    (if (m/validate PostgresType
                    safe-type)
      safe-type
      (throw (ex-info (str t
                           " is not a known postgres type. Please use one of: "
                           (str/join "\n"
                                     (rest PostgresType)))
                      {:found t})))))

(def Attribute
  [:map
   [:name [:or :string :keyword]]
   [:type (into [:or :reference] PostgresType)]
   [:required? {:optional true}
    :boolean]
   [:unique? {:optional true}
    :boolean]
   [:default {:optional true}
    [:or :string :int :boolean [:and :keyword #(= "fn" (namespace %))]]]])

(def MigrationBlueprint
  [:map
   [:model :string]
   [:timestamp :string]
   [:migration-name :string]
   [:table :string]
   [:attributes [:vector Attribute]]])

(defn references? [builder]
  (let [s "references"]
    (= (seq s) (take (count s) builder))))

(defn reference-attr->table-name [builder]
  (->> builder
       (apply str)
       (re-find #"references\((.*)\)")
       second))

(def supports-default-value?
  #{:string :text :citext :integer :bigint :smallint :decimal :float :double
    :boolean :date :datetime :timestamp :timestamptz :uuid :json :jsonb})

(def int-coercion?
  #{:integer :bigint :smallint})

(def double-coercion?
  #{:decimal :float :double})

(def boolean-coercion?
  #{:boolean})

(defn coerce-default [{:keys [type]
                       :as   attr}]
  (cond
    (int-coercion? type)     (update attr :default parse-long)
    (double-coercion? type)  (update attr :default parse-double)
    (boolean-coercion? type) (update attr :default boolean)
    :else                    attr))

(defn parse-attribute [arg]
  (loop [builder   arg
         attribute {:type :text}]
    (if (empty? builder)
      (coerce-default attribute)
      (cond
        (= \^ (first builder))
        (recur (rest builder)
               (assoc attribute
                 :unique? true))

        (= \! (first builder))
        (recur (rest builder)
               (assoc attribute
                 :required? true))

        (= \: (first builder))
        (let [s (apply str
                  (take-while #(not= %
                                     \=)
                              (rest builder)))]
          (recur (apply str
                   (drop (inc (count s))
                         builder))
                 (assoc attribute
                   :type
                   (coerce-to-type (keyword s)))))

        (= \= (first builder))
        (let [default* (apply str
                         (rest builder))
              db-fn?   (str/starts-with? default*
                                         "fn/")
              default  (if db-fn?
                         (->> default*
                              (drop (count "fn/"))
                              (apply str)
                              (keyword "fn"))
                         default*)]
          (recur nil
                 (assoc attribute
                   :default default)))

        (references? builder)
        (merge attribute
               {:type :reference
                :name (-> builder
                          reference-attr->table-name
                          lang/table-name->foreign-key-id
                          keyword)})

        :else
        (let [n (apply str
                  (take-while #(not (#{\: \=}
                                     %))
                              builder))]
          (recur (apply str
                   (drop (count n)
                         builder))
                 (assoc attribute
                   :name
                   (keyword n))))))))

(def default-attributes
  [{:name      :created-at
    :type      :timestamptz
    :required? true
    :default   :fn/now}
   {:name      :updated-at
    :type      :timestamptz
    :required? true
    :default   :fn/now
    :trigger   :update-updated-at}])

(def id-field
  {:name :id
   :type :primary-key})

(defn parse
  "Model name is expected to be plural."
  [base-name cli-args]
  (let [model     (keyword (first cli-args))
        table     model
        blueprint {:model          model
                   :template       :model
                   :timestamp      (time/timestamp)
                   :table          table
                   :migration-name (str base-name "-" (name table))
                   :attributes     [id-field]}]
    (loop [blueprint blueprint
           args      (rest cli-args)]
      (if (empty? args)
        (update blueprint
                :attributes
                into
                default-attributes)
        (recur (update blueprint
                       :attributes
                       conj
                       (parse-attribute (first args)))
               (rest args))))))
