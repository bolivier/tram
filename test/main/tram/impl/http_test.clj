(ns tram.impl.http-test
  (:require [clojure.test :refer [deftest is]]
            [matcher-combinators.test]
            [tram.impl.http :as sut]))

(deftest html-request?
  (is (not (sut/html-request? {:headers {"accept" "application/edn"}})))
  (is (not (sut/html-request? {:headers {"accept" "application/json"}})))
  (is (not (sut/html-request? {:headers {"accept" "*/*"}})))
  (is (sut/html-request? {:headers {"accept" "text/html"}})))

(deftest htmx-request?
  (is (not (sut/htmx-request? {:headers {}})))
  (is (sut/htmx-request? {:headers {"hx-request" "true"}})))

(deftest redirect-static-route-test
  (is (match? {:status  301
               :headers {"hx-redirect" [:tram.html/make :route/login {}]}}
              (sut/redirect :route/login))))

(deftest redirect-dynamic-route-test
  (is (match? {:status  301
               :headers {"hx-redirect" [:tram.html/make
                                        :route/article
                                        {:slug "whatever"}]}}
              (sut/redirect :route/article {:slug "whatever"}))))

(deftest redirect-static-route-with-resp-test
  (is (match? {:status  301
               :headers {"hx-redirect"   [:tram.html/make :route/login {}]
                         "x-data-header" "foobar"}}
              (sut/redirect {:headers {"x-data-header" "foobar"}}
                            :route/login))))

(deftest redirect-dynamic-route-with-resp-test
  (is (match? {:status  301
               :headers {"hx-redirect"   [:tram.html/make
                                          :route/article
                                          {:slug "whatever"}]
                         "x-data-header" "foobar"}}
              (sut/redirect {:headers {"x-data-header" "foobar"}}
                            :route/article
                            {:slug "whatever"}))))

(deftest full-redirect-static-route-test
  (is (match? {:status  303
               :headers {"location" [:tram.html/make :route/login {}]}}
              (sut/full-redirect :route/login))))

(deftest full-redirect-dynamic-route-test
  (is (match? {:status  303
               :headers {"location" [:tram.html/make
                                     :route/article
                                     {:slug "whatever"}]}}
              (sut/full-redirect :route/article {:slug "whatever"}))))

(deftest full-redirect-static-route-with-resp-test
  (is (match? {:status  303
               :headers {"location"      [:tram.html/make :route/login {}]
                         "x-data-header" "foobar"}}
              (sut/full-redirect {:headers {"x-data-header" "foobar"}}
                                 :route/login))))

(deftest full-redirect-dynamic-route-with-resp-test
  (is (match? {:status  303
               :headers {"location"      [:tram.html/make
                                          :route/article
                                          {:slug "whatever"}]
                         "x-data-header" "foobar"}}
              (sut/full-redirect {:headers {"x-data-header" "foobar"}}
                                 :route/article
                                 {:slug "whatever"}))))
