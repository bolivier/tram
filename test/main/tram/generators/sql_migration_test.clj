(ns tram.generators.sql-migration-test
  (:require [clojure.test :refer [deftest is]]
            [rapid-test.core :as rt]
            [tram.core :as tram]
            [tram.generators.sql-migration :as sut]
            [tram.test-fixtures :refer [tram-config]]))

(declare blueprint)

(deftest attribute-parsing
  (doseq [[expected input]
          [[[:attribute :text]
            {:type :text
             :name :attribute}]
           [[:attribute :text :unique]
            {:type    :text
             :name    :attribute
             :unique? true}]
           [[:attribute :text [:not nil]]
            {:type      :text
             :name      :attribute
             :required? true}]
           [[:attribute :text [:not nil] :unique]
            {:type      :text
             :name      :attribute
             :required? true
             :unique?   true}]
           [[:signup-date :timestamptz [:not nil] [:default [:now]]]
            {:type      :timestamptz
             :name      :signup-date
             :required? true
             :default   :fn/now}]
           [[:is-cool :text [:default "yes"]]
            {:type    :text
             :name    :is-cool
             :default "yes"}]
           [[:id :serial :primary :key]
            {:type :primary-key
             :name :id}]
           [[:user-id :integer [:references :users :id]]
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
    (rt/with-stub [_ {:fn      tram/get-migration-config
                      :returns (:database/development tram-config)}
                   calls spit]
      (sut/write-to-migration-file blueprint)
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
                                                  {:type    :timestamptz
                                                   :name    :updated-at
                                                   :trigger :update-updated-at
                                                   :default :fn/now}]})
        output (atom nil)]
    (rt/with-stub [_ {:fn      tram/get-migration-config
                      :returns (:database/development tram-config)}
                   calls spit]
      (sut/write-to-migration-file blueprint-with-multiple-actions)
      (is (match? #"^resources/migrations/\d+-create-table-users.up.sql"
                  (first (:args (first @calls)))))
      (reset! output (second (:args (first @calls)))))
    (rt/match-snapshot @output ::sql-up-multiple-tables-test)))

(deftest sql-migration-down-contents
  (let [output (atom nil)]
    (rt/with-stub [_ {:fn      tram/get-migration-config
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
    (rt/with-stub [_ {:fn      tram/get-migration-config
                      :returns (:database/development tram-config)}
                   calls spit]
      (sut/write-to-migration-down blueprint-with-multiple-actions)
      (is (match? #"^resources/migrations/\d+-create-table-users.down.sql"
                  (first (:args (first @calls)))))
      (reset! output (second (:args (first @calls)))))
    (rt/match-snapshot @output ::sql-down-multiple-tables-test)))

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
                                   :name    "signup_date"
                                   :trigger :update-updated-at
                                   :default :fn/now}]}]})
