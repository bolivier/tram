(ns tram.http.utils-test
  (:require [expectations.clojure.test :as e]
            [tram.http.utils :as sut]))

(e/defexpect html-request?
  (e/expect false (sut/html-request? {:headers {"accept" "application/edn"}}))
  (e/expect false (sut/html-request? {:headers {"accept" "application/json"}}))

  ;; needs to be explicit
  (e/expect false (sut/html-request? {:headers {"accept" "*/*"}}))

  ;; true case
  (e/expect (sut/html-request? {:headers {"accept" "text/html"}}))
)

(e/defexpect htmx-request?
  (e/expect false (sut/htmx-request? {:headers {}}))
  (e/expect (sut/htmx-request? {:headers {"hx-request" "true"}}))
)
