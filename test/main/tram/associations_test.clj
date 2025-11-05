(ns tram.associations-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            kaocha-utils.kaocha-hooks
            [matcher-combinators.matchers :as m]
            matcher-combinators.test
            [toucan2.core :as t2]
            [tram.associations :as sut]
            [tram.language :as lang]))

(defn teardown-db []
  (t2/delete! :models/accounts))

(defn setup-db []
  (alter-var-root #'sut/*associations* (constantly {}))
  (sut/belongs-to! :models/users :models/accounts)
  (sut/has-many! :models/accounts :models/users)
  (sut/has-many! :models/users :models/settings)
  (let [account-id  (t2/insert-returning-pk! :models/accounts {})
        settings-id (t2/insert-returning-pk! :models/settings {})
        _ (t2/insert! (lang/join-table :users :settings)
                      {:setting-id settings-id
                       :user-id    (:id user)})
        bird-id     (t2/insert-returning-pk! :models/birds {})
        user        (t2/insert-returning-instance! :models/users
                                                   {:bird-id    bird-id
                                                    :name       "Brandon"
                                                    :account-id account-id})
        _ (t2/insert-returning-pk! :models/articles
                                   {:title     "My Article"
                                    :author-id (:id user)})
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

(use-fixtures :once (fn [f] (teardown-db) (setup-db) (f) (teardown-db)))

;; These tests are kind of implementation details, but I don't care for now
;; because this is kinda complicated.

(deftest has-many-associations-structure
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
  (let [account (t2/select-one :models/accounts)
        account-with-users (t2/hydrate account :users)]
    (is (pos? (count (:users account-with-users)))
        "Did not have positive user count in belongs-to test")
    (doseq [user (:users account)]
      (is (= (:id account) (:account-id user))))))

(deftest integration-has-one
  (testing "something-different"
    (let [user (t2/select-one :models/users)]
      (is (match? {:account {:id int?}} (t2/hydrate user :account))
          "Account did not match.")))
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
              (t2/model-for-automagic-hydration :models/accounts :owner)))
  (t2/select-one :models/users))

(deftest belongs-to-has-many-alias-test
  (sut/has-one! :models/articles :models/users {:as :author})
  (sut/belongs-to! :models/articles :models/users {:as :author})
  (sut/has-many! :models/users :models/articles {:from :author})
  (let [user          (t2/select-one :models/users :name "Brandon")
        article       (t2/select-one :models/articles :title "My Article")
        hydrated-user (t2/hydrate user :articles)
        hydrated-article (t2/hydrate article :author)]
    (is (= 2 (count (:articles hydrated-user))))
    (is (= (:id user) (:id (:author hydrated-article))))))
