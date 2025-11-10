(ns tram.associations-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            kaocha-utils.kaocha-hooks
            [matcher-combinators.matchers :as m]
            matcher-combinators.test
            [test-migrations.seed-data]
            [toucan2.core :as t2]
            [tram.associations :as sut]
            [tram.language :as lang]))
(defn teardown-db []
  (t2/delete! :models/accounts)
  (t2/delete! :models/users)
  (t2/delete! :models/settings)
  (t2/delete! :models/settings-users))

(defn setup-db []
  (alter-var-root #'sut/*associations* (constantly (atom {})))
  (sut/has-many! :models/accounts :users)
  (sut/has-many! :models/users :settings)
  (sut/has-one! :models/birds :user)
  (sut/has-many! :models/users :computers {:foreign-key :developer-id})
  (sut/has-one! :models/users
                :home
                {:model       :models/addresses
                 :foreign-key :homeowner-id})
  (sut/has-many! :models/users
                 :followers
                 {:join-table  :follows
                  :model-key   :follower-id
                  :foreign-key :followee-id
                  :model       :models/users})
  (sut/has-many! :models/users
                 :follows
                 {:join-table  :follows
                  :model-key   :followee-id
                  :foreign-key :follower-id
                  :model       :models/users})
  (sut/belongs-to! :models/addresses :homeowner {:model :models/users})
  (let [account-id  (t2/insert-returning-pk! :models/accounts {})
        settings-id (t2/insert-returning-pk! :models/settings {})
        bird-id     (t2/insert-returning-pk! :models/birds {})
        user        (t2/insert-returning-instance! :models/users
                                                   {:bird-id    bird-id
                                                    :name       "Brandon"
                                                    :account-id account-id})
        olivia      (t2/insert-returning-instance! :models/users
                                                   {:name       "Olivia"
                                                    :account-id account-id})
        _ (t2/insert! :models/follows
                      {:follower-id (:id olivia)
                       :followee-id (:id user)})
        _ (t2/insert! :models/computers
                      {:developer-id (:id user)
                       :name         "Thinkpad"})
        _ (t2/insert! :models/computers
                      {:developer-id (:id user)
                       :name         "Macbook"})
        _ (t2/insert-returning-pk! :models/articles
                                   {:title     "My Article"
                                    :author-id (:id user)})
        _ (t2/insert! (lang/join-table :users :settings)
                      {:setting-id settings-id
                       :user-id    (:id user)})
        _ (t2/insert-returning-pk! :models/articles
                                   {:title     "My Article 2"
                                    :author-id (:id user)})
        _ (t2/insert-returning-pk! :models/addresses
                                   {:full_address "123 Fake St."
                                    :homeowner-id (:id user)})
        _ (t2/insert-returning-pk! :models/addresses
                                   {:full_address "742 Evergreen Terrace"
                                    :homeowner-id (:id user)})]))

(comment
  (do (teardown-db)
      (setup-db)))

(comment
  (do (require '[tram.tram-config :as tram.config])
      (require '[matcher-combinators.test])
      (require '[migratus.core :as migratus]
               '[next.jdbc :as jdbc]
               '[test-migrations.seed-data]
               '[tram.tram-config :as tram.config])
      ;; On BB, splice in nothing. On JVM Clojure, pull in
      ;; jdbc/migratus/tram.
      (let [migration-config (tram.config/get-migration-config "test")
            config (:db migration-config)]
        (try
          ;; Ensure we run CREATE DATABASE against a management DB.
          (jdbc/execute! (jdbc/get-datasource (assoc config
                                                :dbname "postgres"))
                         ["CREATE DATABASE tram_test"])
          (catch org.postgresql.util.PSQLException _
            ;; most likely already exists
            nil))
        (migratus/init migration-config)
        (migratus/reset migration-config)
        (migratus/migrate migration-config)
        (setup-db))))

(use-fixtures :once (fn [f] (teardown-db) (setup-db) (f)))

;; These tests are kind of implementation details, but I don't care for now
;; because this is kinda complicated.

(defn brandon []
  (t2/select-one :models/users :name "Brandon"))
(defn olivia []
  (t2/select-one :models/users :name "Olivia"))

(deftest has-many-opposite-belongs-to-test
  (let [account (t2/select-one :models/accounts)
        hydrated-account (t2/hydrate account :users)]
    (is (= 2
           (-> hydrated-account
               :users
               count)))
    (is (match? {:id   int?
                 :name string?}
                (-> hydrated-account
                    :users
                    first)))))


(deftest has-many-with-join-test
  (let [hydrated-user (t2/hydrate (brandon) :settings)]
    (is (= 1
           (-> hydrated-user
               :settings
               count)))))

(deftest has-many-opposite-belongs-to-alias-test
  (let [developer (brandon)
        hydrated-developer (t2/hydrate developer :computers)]
    (is (match? {:id   int?
                 :name (m/any-of "Thinkpad" "Macbook")}
                (first (:computers hydrated-developer))))))

(deftest has-one-test
  (let [bird (t2/select-one :models/birds :id (:bird-id (brandon)))
        hydrated-bird (t2/hydrate bird :user)]
    (is (= (brandon) (:user hydrated-bird)))))

(deftest belongs-to-single-test
  (let [account (t2/select-one :models/accounts)]
    (is (match? account (:account (t2/hydrate (brandon) :account))))))

(deftest belongs-to-with-alias
  (let [address (t2/select-one :models/addresses)]
    (is (match? (brandon) (:homeowner (t2/hydrate address :homeowner))))))

(deftest has-many-followers-test
  (let [follower (first (:followers (t2/hydrate (brandon) :followers)))]
    (is (match? (olivia) follower))
    (is (not (:follower-id follower)))))

(deftest has-many-follows-test
  (is (match? (brandon) (first (:follows (t2/hydrate (olivia) :follows))))))
