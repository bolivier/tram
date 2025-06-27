(ns tram.generators.blueprint-test
  (:require [expectations.clojure.test :refer [defexpect expect in]]
            [tram.generators.blueprint :as sut]))

(defexpect parsing-attributes
  (expect {:name "first_name"
           :type :text}
          (sut/parse-attribute "first-name"))

  (expect {:name      "email"
           :type      :citext
           :required? true
           :unique?   true}
          (sut/parse-attribute "!^email:citext"))

  (expect {:name      "created_at"
           :type      :timestamptz
           :required? true
           :default   :fn/now}
          (sut/parse-attribute "!created-at:timestamptz=fn/now"))

  (expect {:name      "team-id"
           :type      :integer
           :required? true}
          (sut/parse-attribute "references(teams)"))

  (expect {:name    "cool"
           :type    :text
           :default "yes"}
          (sut/parse-attribute "cool=yes")))

(defexpect parsing-blueprint
  (let [actual (sut/parse
                 "create-model"
                 ["agent" "!first-name" "^last-name" "references(users)"])]
    (expect {:table "agents"} (in actual))))
