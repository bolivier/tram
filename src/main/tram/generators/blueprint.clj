(ns tram.generators.blueprint
  (:require [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [declensia.core :as dc]
            [selmer.parser :as selmer]
            [selmer.util]
            [tram.core :as tram]
            [tram.utils.english :refer [pluralize]]
            [tram.utils.language :as lang]
            [tram.utils.time :as time]
            [zprint.core :refer [zprint-file-str]]))

(def PostgresType
  [:enum
   :string
   :text
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
       str
       (re-find #"references\((.*)\)")
       second))

(defn parse-attribute [arg]
  (loop [builder   arg
         attribute {:type :text}]
    (if (empty? builder)
      attribute
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
                   (keyword s))))

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
        {:type      :integer
         :name      (-> (reference-attr->table-name builder)
                        lang/table-name->foreign-key-id)
         :required? true}

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
                   (->snake_case n))))))))

(defn parse [base-name cli-args]
  (let [model     (first cli-args)
        table     (dc/pluralize model)
        blueprint {:model          model
                   :timestamp      (time/timestamp)
                   :table          table
                   :migration-name (str base-name "-" table)
                   :attributes     []}]
    (loop [blueprint blueprint
           args      (rest cli-args)]
      (if (empty? args)
        blueprint
        (recur (update blueprint
                       :attributes
                       conj
                       (parse-attribute (first args)))
               (rest args))))))

(defn generate [blueprint]
  (let [ns-prefix   "runtime."
        file-prefix "src/dev/runtime/"
        filename    (str "generate_" (:model blueprint) ".clj")
        namespace   (str ns-prefix
                         (-> filename
                             (str/replace ".clj" "")
                             (str/replace "_" "-")))
        fd          (str file-prefix filename)
        file        (io/file fd)]
    (io/make-parents file)
    (spit fd
          (zprint-file-str (binding [selmer.util/*escape-variables* false]
                             (selmer/render-file
                               "tram/templates/model.template.clj"
                               {:namespace        namespace
                                :blueprint-string blueprint}))
                           ::model-template
                           (tram/get-zprint-config)))))
