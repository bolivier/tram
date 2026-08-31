(ns tram.impl.http-test
  (:require [clojure.test :refer [deftest is]]
            [matcher-combinators.test]
            [tram.impl.http :as sut]))

(deftest html-request?
  (is (not (sut/html-request? {:headers {"accept" "application/edn"}})))
  (is (not (sut/html-request? {:headers {"accept" "application/json"}})))
  (is (not (sut/html-request? {:headers {"accept" "*/*"}})))
  (is (sut/html-request? {:headers {"accept" "text/html"}})))

(deftest rhizome-request?
  (is (not (sut/rhizome-request? {:headers {}})))
  (is (not (sut/rhizome-request? {:headers {"hx-request" "true"}})))
  (is (sut/rhizome-request? {:headers {"rhizome-request" "true"}})))

(deftest redirect-static-route-test
  (is (match? {:status  303
               :headers {"location" [:tram.html/make :route/login {}]}}
              (sut/redirect :route/login))))

(deftest redirect-dynamic-route-test
  (is (match? {:status  303
               :headers {"location" [:tram.html/make
                                     :route/article
                                     {:slug "whatever"}]}}
              (sut/redirect :route/article {:slug "whatever"}))))

(deftest redirect-static-route-with-resp-test
  (is (match? {:status  303
               :headers {"location"      [:tram.html/make :route/login {}]
                         "x-data-header" "foobar"}}
              (sut/redirect {:headers {"x-data-header" "foobar"}}
                            :route/login))))

(deftest redirect-dynamic-route-with-resp-test
  (is (match? {:status  303
               :headers {"location"      [:tram.html/make
                                          :route/article
                                          {:slug "whatever"}]
                         "x-data-header" "foobar"}}
              (sut/redirect {:headers {"x-data-header" "foobar"}}
                            :route/article
                            {:slug "whatever"}))))

(def ^:private req
  {:request-method :get
   :uri "/sign-in"})

(deftest lower-status-keywords
  (is (= {:status 200} (sut/lower-status {:status :ok} req)))
  (is (= {:status 422} (sut/lower-status {:status :unprocessable-entity} req)))
  (is (= {:status 303} (sut/lower-status {:status :redirect} req))))

(deftest lower-status-leaves-other-statuses-alone
  (is (= {:status 201} (sut/lower-status {:status 201} req)))
  (is (= {} (sut/lower-status {} req)))
  (is (nil? (sut/lower-status nil req))))

(deftest lower-status-redirect-vectors
  (is (= {:status  303
          :headers {"location" [:tram.html/make :route/login {}]}}
         (sut/lower-status {:status [:redirect :route/login]} req)))
  (is (= {:status  303
          :headers {"location" [:tram.html/make :route/article {:slug "x"}]}}
         (sut/lower-status {:status [:see-other :route/article {:slug "x"}]}
                           req)))
  (is (= {:status  301
          :headers {"location" "https://example.com"}}
         (sut/lower-status {:status [:moved-permanently "https://example.com"]}
                           req))))

(deftest lower-status-throws-on-unknown-keywords
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Unknown status keyword :okk in the response to GET /sign-in"
        (sut/lower-status {:status :okk} req)))
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Unknown status keyword :redirekt in the response to GET /sign-in"
        (sut/lower-status {:status [:redirekt :route/login]} req))))

(deftest lower-status-throws-on-malformed-redirect-vectors
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"must start with a 3xx keyword; :ok is 200"
                        (sut/lower-status {:status [:ok :route/home]} req)))
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"passes params, but a string target takes none"
        (sut/lower-status {:status [:redirect "https://x.dev" {:a 1}]} req)))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"needs a route keyword or a string as its target"
                        (sut/lower-status {:status [:redirect 42]} req)))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"takes \[status target params\?\]"
                        (sut/lower-status {:status [:redirect]} req))))
