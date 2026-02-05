(ns tram.html-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [matcher-combinators.test]
            [muuntaja.format.core :as mfc]
            [tram.html :as sut]
            [tram.test-fixtures :refer [sample-router]]
            [tram.vars :refer [*req*]]))

(deftest make-simple-path-test
  (is (= "/dashboard" (sut/make-path sample-router :route/dashboard))))

(deftest make-path-with-simple-param-test
  (is (= "/dashboard/users/1"
         (sut/make-path sample-router :route/user {:user-id 1}))))

(deftest makes-path-with-arbitrary-params-test
  (is (= "/dashboard/users/foobar"
         (sut/make-path sample-router :route/user {:user-id "foobar"})))
  (is (= "/dashboard/users/foobar"
         (sut/make-path sample-router :route/user {:user-id :foobar}))))

(deftest makes-path-nil-when-required-params-missing-test
  (is (= nil (sut/make-path sample-router :route/user))))

(deftest makes-path-with-query-params-test
  (let [q-part      (fn [url] (second (re-find #"\?(.*$)" url)))
        construct   (fn [m]
                      (sut/make-path sample-router
                                     :route/user
                                     {:user-id 1
                                      :tram.routes/query m}))
        make-q-part (comp q-part construct)]
    (doseq [[expected input] [["foo=bar" {:foo :bar}]
                              ["foo=bar" {'foo 'bar}]
                              ["foo=bar" {"foo" "bar"}]
                              ["foo=1" {"foo" 1}]
                              [nil {}]
                              ;; TODO add collection support.  Nested?
                              #_["foo[]=2&foo[]=3" {:foo [2 3]}]]]
      (is (match? expected (make-q-part input))))))

(deftest encode-to-output-bytes-test
  (let [encoder (sut/huff-html-encoder nil)]
    (binding [*req* {:reitit.core/router sample-router}]
      (let [output (apply str
                     (map char
                       (mfc/encode-to-bytes encoder [:div "hello"] "UTF-8")))]
        (is (= output "<div>hello</div>"))))))

(deftest encode-to-output-stream-test
  (let [encoder (sut/huff-html-encoder nil)
        baos    (java.io.ByteArrayOutputStream.)]
    (binding [*req* {:reitit.core/router sample-router}]
      (let [encode-fn
            (mfc/encode-to-output-stream encoder [:div "hello"] "UTF-8")]
        (encode-fn baos))
      (is (str/includes? (.toString baos "UTF-8") "<div>")))))
