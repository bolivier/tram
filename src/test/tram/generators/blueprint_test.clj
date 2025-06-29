(ns tram.generators.blueprint-test
  (:require [clojure.test :as t]
            [expectations.clojure.test :as e :refer [defexpect expect in]]
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

  (expect {:name       :team-id
           :type       :integer
           :required?  true
           :references :teams}
          (sut/parse-attribute "references(teams)"))

  (expect {:name    :cool
           :type    :text
           :default "yes"}
          (sut/parse-attribute "cool=yes")))

(def timestamp
  "20250628221924")
(e/defexpect parsing-blueprint
  (with-redefs [tram.utils.time/timestamp (constantly timestamp)]
    (let [actual (sut/parse
                   "create-model"
                   ["agents" "!first-name" "^last-name" "references(users)"])]
      (e/expect {:table          :agents
                 :model          :agents
                 :timestamp      timestamp
                 :migration-name "create-model-agents"
                 :template       :model}
                (dissoc actual :attributes))
      (e/expect {:name      :updated-at
                 :type      :timestamptz
                 :required? true
                 :default   :fn/now
                 :trigger   :update-updated-at}
                (e/in (:attributes actual))))))

(comment
  ;; PRINCIPLE: strings are meant to be rendered as is.  Keywords are meant
  ;; to be passed around as data.
  (def blueprints
    {:model          :agent
     :template       :model
     :timestamp      "20250628191533"
     :table          :agents
     :migration-name "create-model-agents"
     :attributes     [{:type      :text
                       :required? true
                       :name      "first_name"}
                      {:type    :text
                       :unique? true
                       :name    "last_name"}
                      {:type      :integer
                       :name      "user-id"
                       :required? true}
                      {:name      :created-at
                       :type      :timestamptz
                       :required? true
                       :default   :fn/now}
                      {:name      :updated-at
                       :type      :timestamptz
                       :required? true
                       :default   :fn/now
                       :trigger   :updated-trigger}]}))
