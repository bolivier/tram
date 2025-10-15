(ns tram.generators.blueprint-test
  (:require [clojure.test :as t]
            [expectations.clojure.test :as e :refer [defexpect expect in]]
            [rapid-test.core :as rt]
            [tram.generators.blueprint :as sut]))

(defexpect parsing-attributes
  (expect {:name :first-name
           :type :text}
          (sut/parse-attribute "first-name"))

  (expect {:name      :email
           :type      :citext
           :required? true
           :unique?   true}
          (sut/parse-attribute "!^email:citext"))

  (expect {:name      :created-at
           :type      :timestamptz
           :required? true
           :default   :fn/now}
          (sut/parse-attribute "!created-at:timestamptz=fn/now"))

  (expect {:name      :team-id
           :type      :reference
           :required? true}
          (sut/parse-attribute "!references(teams)"))

  (expect {:name    :is-good
           :type    :boolean
           :default true}
          (sut/parse-attribute "is-good:boolean=true"))

  (expect {:name    :percentage
           :type    :double
           :default 0.1}
          (sut/parse-attribute "percentage:double=0.1"))

  (expect {:name    :likes
           :type    :integer
           :default 0}
          (sut/parse-attribute "likes:int=0"))

  (expect {:name    :cool
           :type    :text
           :default "yes"}
          (sut/parse-attribute "cool=yes")))

(def timestamp
  "20250628221924")

(e/defexpect parsing-blueprint
  (rt/with-stub [_ {:fn      tram.time/timestamp
                    :returns timestamp}]
    (let [actual (sut/parse
                   "create-model"
                   ["agents" "!first-name" "^last-name" "references(users)"])]
      (e/expect {:table          :agents
                 :model          :agents
                 :timestamp      timestamp
                 :migration-name "create-model-agents"
                 :template       :model}
                (dissoc actual :attributes))
      (e/expect {:name :id
                 :type :primary-key}
                (e/in (:attributes actual)))
      (e/expect {:name      :updated-at
                 :type      :timestamptz
                 :required? true
                 :default   :fn/now
                 :trigger   :update-updated-at}
                (e/in (:attributes actual))))))

(comment
  ;; PRINCIPLE: strings are meant to be rendered as is.  Keywords are meant
  ;; to be passed around as data.
  (def blueprint
    {:model          :agents
     :template       :model
     :timestamp      "20250701130612"
     :table          :agents
     :migration-name "create-model-agents"
     :attributes     [{:name :id
                       :type :primary-key}
                      {:type      :text
                       :required? true
                       :name      :first-name}
                      {:type    :text
                       :unique? true
                       :name    :last-name}
                      {:type      :citext
                       :unique?   true
                       :required? true
                       :name      :email}
                      {:type      :reference
                       :name      :account-id
                       :required? true}
                      {:name      :created-at
                       :type      :timestamptz
                       :required? true
                       :default   :fn/now}
                      {:name      :updated-at
                       :type      :timestamptz
                       :required? true
                       :default   :fn/now
                       :trigger   :update-updated-at}]}))
