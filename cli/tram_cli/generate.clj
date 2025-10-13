(ns tram-cli.generate
  "TODO review this ns.  I wrote it in a rush.  This is whatever.  I copypasta'd"
  (:require [camel-snake-kebab.core :refer [->snake_case_string]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [malli.core :as m]
            [selmer.parser :as selmer]
            [selmer.util]
            [tram.utils.config :refer [get-zprint-config]]
            [tram.utils.language :as lang]
            [zprint.core :refer [zprint-file-str]])
  (:import java.text.SimpleDateFormat
           [java.util Date TimeZone]))

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

(defn references? [builder]
  (->> builder
       (apply str)
       (re-find #"references\((.*)\)")
       boolean))

(defn reference-attr->table-name [builder]
  (->> builder
       (apply str)
       (re-find #"references\((.*)\)")
       second))

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
        (let [[_ name] (re-find #"([^:]+):references\(([^ ]+)\).*"
                                (apply str
                                  builder))]
          (merge attribute
                 {:type  :reference
                  :table (-> builder
                             reference-attr->table-name
                             keyword)
                  :name  (or name
                             (-> builder
                                 reference-attr->table-name
                                 lang/table-name->foreign-key-id
                                 keyword))}))

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


(def id-field
  {:name :id
   :type :primary-key})

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

(defn parse
  "Parse a blueprint from the cli args"
  [base-name cli-args]
  (let [blueprint {:template       :dev-runtime
                   :table          nil
                   :timestamp      (let [fmt (doto (SimpleDateFormat.
                                                     "yyyyMMddHHmmss")
                                               (.setTimeZone
                                                 (TimeZone/getTimeZone "UTC")))]
                                     (.format fmt (Date.)))
                   :migration-name base-name
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

(def runtime-defaults
  {:root "runtimes"
   :path "dev/runtimes/"})

(defn get-runtime-filename [blueprint]
  (let [filename (str "generate_"
                      (->snake_case_string (:migration-name blueprint))
                      ".clj")]
    (str (:path runtime-defaults) filename)))

(defn get-runtime-ns [blueprint]
  (let [filename  (-> blueprint
                      get-runtime-filename
                      (str/split #"/")
                      last)
        namespace (str (:root runtime-defaults)
                       "."
                       (-> filename
                           (str/replace ".clj" "")
                           (str/replace "_" "-")))]
    namespace))

(defn get-template [blueprint]
  (str "tram/templates/"
       (-> blueprint
           :template
           ->snake_case_string)
       ".clj.template"))

(defn format-code [clj-source-string]
  (zprint-file-str clj-source-string ::model-template (get-zprint-config)))

(defn render
  "Render a blueprint into runtime code.

  Returns an unformatted string."
  [blueprint]
  (binding [selmer.util/*escape-variables* false]
    (selmer/render-file (get-template blueprint)
                        {:namespace        (get-runtime-ns blueprint)
                         :blueprint-string blueprint})))


(defn write [blueprint]
  (let [filename (get-runtime-filename blueprint)
        file     (io/file (io/file (System/getenv "TRAM_CLI_CALLED_FROM"))
                          filename)]
    (io/make-parents file)
    (spit file
          (-> blueprint
              render
              format-code))))

(comment
  (def name
    "add-projects")
  (def args
    (list "^!slug" "!title" "!description" "!body" "references(users)")))
(defn generate-migration-runtime [[name & args]]
  (let [blueprint (parse name args)]
    (write blueprint)))

(defn do-generate [args]
  (let [[what-to-generate & args] args]
    (cond
      (= "migration" what-to-generate) (generate-migration-runtime args)
      :else (println "Could not find any matching generator."))))
