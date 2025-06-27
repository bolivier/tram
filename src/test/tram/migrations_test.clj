(ns tram.migrations-test
  (:require [expectations.clojure.test :as e]
            [tram.migrations :as sut]))


(def blueprint
  {:model      "user"
   :table      "users"
   :attributes [{:type      :text
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

(e/defexpect serializing-attributes
  (e/expect [:attribute :text]
            (sut/serialize-attribute {:type :text
                                      :name :attribute}))

  (e/expect [:attribute :text :unique]
            (sut/serialize-attribute {:type    :text
                                      :name    :attribute
                                      :unique? true}))

  (e/expect [:attribute :text [:not nil]]
            (sut/serialize-attribute {:type      :text
                                      :name      :attribute
                                      :required? true}))

  (e/expect [:attribute :text [:not nil] :unique]
            (sut/serialize-attribute {:type      :text
                                      :name      :attribute
                                      :required? true
                                      :unique?   true}))

  (e/expect [:signup-date :timestamptz [:not nil] [:default [:now]]]
            (sut/serialize-attribute {:type      :timestamptz
                                      :name      :signup-date
                                      :required? true
                                      :default   :fn/now}))

  (e/expect [:is-cool :text [:default "yes"]]
            (sut/serialize-attribute {:type    :text
                                      :name    :is-cool
                                      :default "yes"}))

  (e/expect [:team-id :integer [:references :teams :id]]
            (sut/serialize-attribute {:type :reference
                                      :name :team-id}))


)

(e/defexpect serialize-to-sql
  (e/expect (sut/serialize-to-sql blueprint)))

(comment
  (require '[tram.generators.model :refer [parse-blueprint]])
  (def blueprint
    (parse-blueprint ["users"
                      "!name"
                      "^!email:citext"
                      "cool=yes"
                      "signup_date:timestamptz=fn/now"])))
