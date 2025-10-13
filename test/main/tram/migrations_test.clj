(ns tram.migrations-test
  (:require [clojure.string :as str]
            [expectations.clojure.test :as e]
            [tram.core :as tram]
            [tram.migrations :as sut]
            [tram.test-fixtures :refer [tram-config]]))

(def blueprint
  {:model          "user"
   :template       "model"
   :timestamp      "20250627163855"
   :table          "users"
   :migration-name "create-table-users"
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
                     :trigger :update-updated-at
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

  (e/expect [:id :serial :primary :key]
            (sut/serialize-attribute {:type :primary-key
                                      :name :id}))

  (e/expect [:user-id :integer [:references :users :id]]
            (sut/serialize-attribute {:type :reference
                                      :name :user-id})))

(e/defexpect getting-names
  (let [calls (atom [])]
    (with-redefs [tram/get-migration-config (constantly (:database/development
                                                          tram-config))
                  spit (fn [fd content]
                         (swap! calls (fn [calls] (conj calls [fd content]))))]
      (sut/write-to-migration-file blueprint)
      (let [[filename contents] (first @calls)
            contents (-> contents
                         (str/replace #"\n+" " ")
                         (str/replace #" +" " ")
                         (str/replace #"\( ?" "(")
                         (str/replace #" ?\)" ")"))]
        (e/expect (str "resources/migrations/"
                       (:timestamp blueprint)
                       "-create-table-users.up.sql")
                  filename
                  "spit was called with incorrect filename")
        (e/expect
          "CREATE TABLE users (name TEXT NOT NULL, email CITEXT NOT NULL UNIQUE, cool TEXT DEFAULT 'yes', signup_date TIMESTAMPTZ DEFAULT NOW()) --;; CREATE TRIGGER set_updated_at_on_users BEFORE UPDATE ON users FOR EACH row EXECUTE FUNCTION update_updated_at_column()"
          contents
          "spit was called with incorrect contents"))))

  (let [calls (atom [])]
    (with-redefs [tram/get-migration-config (constantly (:database/development
                                                          tram-config))
                  spit (fn [fd content]
                         (swap! calls (fn [calls] (conj calls [fd content]))))]
      (sut/write-to-migration-down blueprint)
      (let [[filename contents] (first @calls)]
        (e/expect (str "resources/migrations/"
                       (:timestamp blueprint)
                       "-create-table-users.down.sql")
                  filename
                  "spit was called with incorrect filename")
        (e/expect "DROP TABLE users"
                  contents
                  "spit was called with incorrect contents")))))
