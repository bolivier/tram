(ns tram.generators.domain-test
  (:require [expectations.clojure.test :refer [defexpect expect]]
            [tram.generators.domain :as sut]))

(defexpect model-to-postgres
  (expect
    [[:CREATE
      :TABLE
      :users
      [{:column-name :id
        :type        :serial
        :primary?    true}
       {:column-name :name
        :type        :text
        :required?   true}
       {:column-name :email
        :type        :citext
        :required?   true}
       {:column-name :password
        :type        :text
        :required?   true}
       {:column-name :created_at
        :type        :timestamptz
        :require?    true
        :default     :fn/now}
       {:column-name :updated_at
        :type        :timestamptz
        :require?    true
        :default     :fn/now}]]]
    (sut/generate-migration-code :users
                                 [[:name :text {:required true}]
                                  [:email
                                   :text
                                   {:required true
                                    :unique   true
                                    :case     :insensitive}]
                                  [:password
                                   :text
                                   {:required true
                                    :secret   true}]
                                  #_[:has-many :teams]])))
