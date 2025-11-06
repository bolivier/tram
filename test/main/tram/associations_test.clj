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
  (sut/has-many! :models/accounts :models/users)
  (sut/has-many! :models/users :models/settings)
  (let [account-id  (t2/insert-returning-pk! :models/accounts {})
        settings-id (t2/insert-returning-pk! :models/settings {})
        bird-id     (t2/insert-returning-pk! :models/birds {})
        user        (t2/insert-returning-instance! :models/users
                                                   {:bird-id    bird-id
                                                    :name       "Brandon"
                                                    :account-id account-id})
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
                                    :homeowner-id (:id user)})
        _ (t2/insert-returning-instance! :models/users
                                         {:name       "Olivia"
                                          :account-id account-id})]))

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
    (prn hydrated-user)
    (prn (t2/select :models/settings))
    (prn (t2/select :models/settings-users))
    (is (= 1
           (-> hydrated-user
               :settings
               count)))))

#_(deftest has-one-test
    (sut/has-one! :models/birds :models/users) ;; birds have one user
    (let [bird (t2/select-one :models/birds :id (:bird-id brandon))
          hydrated-bird (t2/hydrate bird :user)]
      (is (= map? (:user hydrated-bird)))))

(deftest belongs-to-single-test
  (let [account (t2/select-one :models/accounts)]
    (is (match? account (:account (t2/hydrate (brandon) :account))))))

;; belongs-to many

;; belongs-to alias

;; has-one

;; has-one alias

;; has-one (join table)

;; has-one (join table) alias

;; has-many (opposite belongs-to)

;; has-many alias (opposite belongs-to)

;; has-many (join table)

;; has-many (join table) alias

;; has-and-belongs-to-many

;; has-and-belongs-to-many alias
