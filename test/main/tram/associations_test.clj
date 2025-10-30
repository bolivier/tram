(ns tram.associations-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [matcher-combinators.matchers :as m]
            matcher-combinators.test
            [toucan2.core :as t2]
            [tram.associations :as sut]
            [tram.language :as lang]))

(defn teardown-db []
  (t2/delete! :models/accounts))

(defn setup-db []
  (sut/belongs-to! :models/users :models/accounts)
  (sut/has-many! :models/accounts :models/users)
  (sut/has-many! :models/users :models/settings)
  (let [account-id  (t2/insert-returning-pk! :models/accounts {})
        settings-id (t2/insert-returning-pk! :models/settings {})
        bird-id     (t2/insert-returning-pk! :models/birds {})
        user        (t2/insert-returning-instance! :models/users
                                                   {:bird-id    bird-id
                                                    :name       "Brandon"
                                                    :account-id account-id})
        user2       (t2/insert-returning-instance! :models/users
                                                   {:name       "Olivia"
                                                    :account-id account-id})]
    (t2/insert! (lang/join-table :users :settings)
                {:setting-id settings-id
                 :user-id    (:id user)})))

(use-fixtures :once (fn [f] (teardown-db) (setup-db) (f) (teardown-db)))

;; These tests are kind of implementation details, but I don't care for now
;; because this is kinda complicated.

(deftest belongs-to-associations-structure
  (is (match? (m/embeds #{:models/accounts})
              (get-in @sut/*associations* [:models/users :belongs-to])))
  (is (sut/belongs-to? :models/users :models/accounts))
  (is (sut/has-explicit-association? :models/users :account)))

(deftest has-many-associations-structre
  (is (match? {:models/accounts {:has-many {:models/users {:through nil}}}}
              @sut/*associations*))
  (is (sut/has-many? :models/accounts :models/users))
  (is (sut/has-explicit-association? :models/accounts :users)))

(deftest has-many-with-join-associations-structure
  (is (match? {:models/users {:has-many {:models/settings {:through
                                                           :settings-users}}}}
              @sut/*associations*))
  (is (sut/has-many? :models/users :models/settings))
  (is (sut/has-explicit-association? :models/users :settings)))

(deftest integration-belongs-to
  (let [user (t2/select-one :models/users)]
    (is (match? {:account {:id int?}} (t2/hydrate user :account))
        "Account did not match.")))

(deftest integration-has-one
  (testing "with value"
    (is (match? {:bird {:id int?}}
                (t2/hydrate (t2/select-one :models/users :name "Brandon")
                            :bird))
        "User did not match"))
  (testing "without value"
    (is (match? {:bird nil?}
                (t2/hydrate (t2/select-one :models/users :name "Olivia") :bird))
        "User did not match"))
  (is (match? {:bird    {:id int?}
               :account {:id int?}}
              (t2/hydrate (t2/select-one :models/users) :bird :account))
      "User did not match"))

(deftest integration-has-many
  (let [account (t2/select-one :models/accounts)]
    (is (match? (m/embeds [(t2/select-one :models/users :name "Brandon")
                           (t2/select-one :models/users :name "Olivia")])
                (:users (t2/hydrate account :users))))))

(deftest integration-has-many-join-table
  (is (not (t2/model-for-automagic-hydration :models/accounts :users)))
  (let [user (t2/select-one :models/users)]
    (is (seq (:settings (t2/hydrate user :settings))))
    (is (match? {:settings [{:id int?}]} (t2/hydrate user :settings)))))

(deftest has-one-alias
  (sut/has-one! :models/accounts :models/users {:as :owner})
  (is (match? :models/users
              (t2/model-for-automagic-hydration :models/accounts :owner))))
