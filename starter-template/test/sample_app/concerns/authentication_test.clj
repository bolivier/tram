(ns sample-app.concerns.authentication-test
  (:require [clojure.test :refer [deftest is]]
            [matcher-combinators.test]
            [sample-app.concerns.authentication :as sut]))

(deftest set-session-cookie-test
  (is (match? {:headers {"Set-Cookie" #"session-id=123"}}
              (sut/set-session-cookie {} 123))))

(deftest simple-parse-cookie-test
  (is (match?
        {"session-id" "123"
         "Path"       "/"
         "Max-Age"    #"\d+"
         "SameSite"   "strict"}
        (sut/parse-cookies
          "session-id=123; Path=/; Max-Age=86400; HttpOnly; SameSite=strict"))))
