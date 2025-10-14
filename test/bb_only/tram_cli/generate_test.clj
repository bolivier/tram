(ns tram-cli.generate-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test]
            [tram-cli.generate :as sut]))

(declare blueprint attribute-cli-args)

(deftest getting-runtime-ns
  (is (= "runtimes.generate-create-table-users"
         (sut/get-runtime-ns blueprint))))

(deftest get-template-file
  (is (= "tram/templates/model.clj.template" (sut/get-template blueprint))))

(deftest get-runtime-file-name
  (is (= "dev/runtimes/generate_create_table_users.clj"
         (sut/get-runtime-filename blueprint))))


(deftest parsing-from-cli-args
  (let [actual (sut/parse "create-table-users" attribute-cli-args)]
    (is
      (match? {:migration-name "create-table-users"
               :timestamp      #"\d+"
               :actions        [{:type       :create-table
                                 :table      "users"
                                 :attributes [{:name :id
                                               :type :primary-key}
                                              {:type      :citext
                                               :required? true
                                               :unique?   true
                                               :name      :email}
                                              {:type    :text
                                               :name    :cool
                                               :default "yes"}
                                              {:type      :text
                                               :required? true
                                               :name      :signup-date
                                               :default   :fn/now}
                                              {:type  :reference
                                               :table :accounts
                                               :name  :account-id}
                                              {:name      :created-at
                                               :type      :timestamptz
                                               :required? true
                                               :default   :fn/now}
                                              {:name :updated-at
                                               :type :timestamptz
                                               :required? true
                                               :default :fn/now
                                               :trigger :update-updated-at}]}]}
              actual))))

(def attribute-cli-args
  ["!name"
   "!^email:citext"
   "cool=yes"
   "!signup-date=fn/now"
   "references(accounts)"])

(def blueprint
  {:migration-name "create-table-users"
   :timestamp      "20251013212940"
   :actions        [{:type       :create-table
                     :table      "users"
                     :attributes [{:name :id
                                   :type :primary-key}
                                  {:type      :citext
                                   :required? true
                                   :unique?   true
                                   :name      :email}
                                  {:type    :text
                                   :name    :cool
                                   :default "yes"}
                                  {:type      :text
                                   :required? true
                                   :name      :signup-date
                                   :default   :fn/now}
                                  {:type  :reference
                                   :table :accounts
                                   :name  :account-id}
                                  {:name      :created-at
                                   :type      :timestamptz
                                   :required? true
                                   :default   :fn/now}
                                  {:name      :updated-at
                                   :type      :timestamptz
                                   :required? true
                                   :default   :fn/now
                                   :trigger   :update-updated-at}]}]})
