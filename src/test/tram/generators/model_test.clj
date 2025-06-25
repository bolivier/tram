(ns tram.generators.model-test
  (:require [expectations.clojure.test :refer [defexpect expect in]]
            [tram.generators.model :as sut]))

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
          (sut/parse-attribute "!created-at:timestamptz=fn/now")))

(defexpect parsing-blueprint
  (let [actual (sut/parse-blueprint
                 ["agent" "first-name" "last-name" "user-id:integer"])]
    (expect {:name  "agent"
             :table "agents"}
            (in actual))))
"tram generate:model !first-name !last-name !^email:citext signup_date=fn/now"
