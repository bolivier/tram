(ns tram.generators.model
  "Generators about models"
  (:require [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [selmer.parser :as selmer]
            [selmer.util]
            [tram.core :as tram]
            [tram.schemas :refer [defschema]]
            [tram.utils.english :refer [pluralize]]
            [zprint.core :refer [zprint-file-str]]))

(def postgres-types
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

(def attribute-options
  [:map])

(def attribute
  [:map
   [:name [:or :string :keyword]]
   [:type postgres-types]
   [:required? {:optional true}
    :boolean]
   [:unique? {:optional true}
    :boolean]
   [:default {:optional true}
    [:or :string :int :boolean [:and :keyword #(= "fn" (namespace %))]]]])

(def NewModelBlueprint
  [:map [:name :string] [:attributes [:vector attribute]]])

(defn optional? [s]
  (str/starts-with? s "?"))
(defn required? [s]
  (str/starts-with? s "!"))

(defn definition [s]
  (let [[name type]   (str/split s #":")
        char-scrubber (fn [s]
                        (if (str/starts-with? s
                                              "!")
                          (subs s
                                1)
                          s))]
    [(char-scrubber name) (or type :text)]))



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

        :else
        (let [n (apply str
                  (take-while #(not= %
                                     \:)
                              builder))]
          (recur (apply str
                   (drop (count n)
                         builder))
                 (assoc attribute
                   :name
                   (->snake_case n))))))))

(defn parse-blueprint [cli-args]
  (let [model     (first cli-args)
        blueprint {:model      model
                   :table      (pluralize model)
                   :attributes []}]
    (loop [blueprint blueprint
           args      (rest cli-args)]
      (if (empty? args)
        blueprint
        (recur (update blueprint
                       :attributes
                       conj
                       (parse-attribute (first args)))
               (rest args))))))

(alter-var-root #'selmer.util/*escape-variables* (constantly false))
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
          (zprint-file-str (selmer/render-file
                             "tram/templates/model.template.clj"
                             {:namespace        namespace
                              :blueprint-string blueprint})
                           ::model-template
                           (tram/get-zprint-config)))))


(comment
  (alter-var-root #'selmer.util/*escape-variables* (constantly false))
  (spit "resources-test-file.clj"
        (zprint-file-str (selmer/render-file "tram/templates/model.template.clj"
                                             {:namespace        "brandon"
                                              :blueprint-string [:foo "bar"]})
                         ::template
                         (tram/get-zprint-config)))
  (io/resource "tram/templates/model.template.clj")
  (do (def template-path
           "tram/templates/model.template.clj")
      (def cli-args
           ["friend"
            "!first-name"
            "!last-name"
            "!^email:citext"
            "signup_date:timestamptz=fn/now"])
      (def namespace
           "model.whatever")
      (def blueprint
           (parse-blueprint cli-args))))
