(ns tram.impl.http-test
  (:require [clojure.test :refer [deftest is]]
            [tram.impl.http :as sut]))

(deftest html-request?
  (is (not (sut/html-request? {:headers {"accept" "application/edn"}})))
  (is (not (sut/html-request? {:headers {"accept" "application/json"}})))
  (is (not (sut/html-request? {:headers {"accept" "*/*"}})))
  (is (sut/html-request? {:headers {"accept" "text/html"}})))

(deftest htmx-request?
  (is (not (sut/htmx-request? {:headers {}})))
  (is (sut/htmx-request? {:headers {"hx-request" "true"}})))
