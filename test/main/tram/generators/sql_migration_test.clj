(ns tram.generators.sql-migration-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test]
            [rapid-test.core :as rt]
            [tram.core :as tram]
            [tram.generators.sql-migration :as sut]
            [tram.test-fixtures :refer [tram-config]]
            [tram.tram-config :as tram.config]))

(declare blueprint)

(deftest attribute-parsing
  (doseq [[expected input]
          [[[:attribute :text [:not nil]]
            {:type :text
             :name :attribute}]
           [[:attribute :text [:not nil] :unique]
            {:type    :text
             :name    :attribute
             :unique? true}]
           [[:attribute :text]
            {:type      :text
             :name      :attribute
             :required? false}]
           [[:attribute :text [:not nil] :unique]
            {:type      :text
             :name      :attribute
             :required? true
             :unique?   true}]
           [[:signup-date :timestamptz [:not nil] [:default [:now]]]
            {:type    :timestamptz
             :name    :signup-date
             :default :fn/now}]
           [[:is-cool :text [:default "yes"]]
            {:type      :text
             :name      :is-cool
             :required? false
             :default   "yes"}]
           [[:id :serial :primary :key]
            {:type :primary-key
             :name :id}]
           [[:user-id :integer [:not nil] [:references :users :id]]
            {:type :reference
             :name :user-id}]
           [[:terms-id :integer [:not nil] [:references :terms :id]]
            {:type       :reference
             :name       :terms-id
             :table-name :terms}]]]
    (testing (str "Attribute " (:name input) " of type " (:type input))
      (is (= expected (sut/serialize-attribute input))))))

(deftest sql-migration-up-contents
  (let [output (atom nil)]
    (rt/with-stub [_ {:fn      tram.config/get-migration-config
                      :returns (:database/development tram-config)}
                   calls spit]
      (sut/write-to-migration-up blueprint)
      (is (match? #"^resources/migrations/\d+-create-table-users.up.sql"
                  (first (:args (first @calls)))))
      (reset! output (second (:args (first @calls)))))
    (rt/match-snapshot @output ::sql-up-test)))

(deftest sql-migration-up-multiple-contents
  (let [blueprint-with-multiple-actions (update blueprint
                                                :actions
                                                conj
                                                {:type :create-table
                                                 :table "accounts"
                                                 :attributes
                                                 [{:type      :text
                                                   :required? true
                                                   :name      :cc-number}
                                                  {:type      :reference
                                                   :required? true
                                                   :index?    true
                                                   :name      :user-id}
                                                  {:type      :text
                                                   :required? true
                                                   :unique?   true
                                                   :index?    true
                                                   :name      :username}
                                                  {:type    :timestamptz
                                                   :name    :updated-at
                                                   :trigger :update-updated-at
                                                   :default :fn/now}]})
        output (atom nil)]
    (rt/with-stub [_ {:fn      tram.config/get-migration-config
                      :returns (:database/development tram-config)}
                   calls spit]
      (sut/write-to-migration-up blueprint-with-multiple-actions)
      (is (match? #"^resources/migrations/\d+-create-table-users.up.sql"
                  (first (:args (first @calls)))))
      (reset! output (second (:args (first @calls)))))
    (rt/match-snapshot @output ::sql-up-multiple-tables-test)))

(deftest sql-migration-down-contents
  (let [output (atom nil)]
    (rt/with-stub [_ {:fn      tram.config/get-migration-config
                      :returns (:database/development tram-config)}
                   calls spit]
      (sut/write-to-migration-down blueprint)
      (is (match? #"^resources/migrations/\d+-create-table-users.down.sql"
                  (first (:args (first @calls)))))
      (reset! output (second (:args (first @calls)))))
    (rt/match-snapshot @output ::sql-down-test)))

(deftest sql-migration-down-multiple-contents
  (let [blueprint-with-multiple-actions (update blueprint
                                                :actions
                                                conj
                                                {:type :create-table
                                                 :table "accounts"
                                                 :attributes
                                                 [{:type      :text
                                                   :required? true
                                                   :name      :cc-number}
                                                  {:type    :timestamptz
                                                   :name    :updated-at
                                                   :trigger :update-updated-at
                                                   :default :fn/now}]})
        output (atom nil)]
    (rt/with-stub [_ {:fn      tram.config/get-migration-config
                      :returns (:database/development tram-config)}
                   calls spit]
      (sut/write-to-migration-down blueprint-with-multiple-actions)
      (is (match? #"^resources/migrations/\d+-create-table-users.down.sql"
                  (first (:args (first @calls)))))
      (reset! output (second (:args (first @calls)))))
    (rt/match-snapshot @output ::sql-down-multiple-tables-test)))

(deftest add-column-to-up-sql-string
  (is (match? "ALTER TABLE \"users\" ADD COLUMN name TEXT"
              (sut/to-up-sql-string {:type   :add-column
                                     :table  "users"
                                     :column {:name      "name"
                                              :type      :text
                                              :required? false}})))
  (is (match? "ALTER TABLE \"users\" ADD COLUMN name TEXT NOT NULL UNIQUE"
              (sut/to-up-sql-string {:type   :add-column
                                     :table  "users"
                                     :column {:name    "name"
                                              :type    :text
                                              :unique? true}})))
  (rt/match-snapshot (sut/to-up-sql-string {:type   :add-column
                                            :table  "users"
                                            :column {:name      "name"
                                                     :type      :text
                                                     :index?    true
                                                     :required? true
                                                     :unique?   true}})
                     ::add-column-sql-string))

(def blueprint
  {:migration-name "create-table-users"
   :timestamp      "20250627163855"
   :actions        [{:type       :create-table
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
                                   :name    :updated-at
                                   :trigger :update-updated-at
                                   :default :fn/now}
                                  {:type    :timestamptz
                                   :name    :created-at
                                   :default :fn/now}]}]})
