(ns tram.wire-format-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test]
            [tram.wire-format :as sut]))

(def ^:private enter
  (:enter sut/rhizome-multipart-interceptor))

(defn- upload [filename]
  {:filename     filename
   :content-type "image/png"
   :size         3
   :tempfile     (java.io.File. filename)})

(defn- body-params-after [multipart-params]
  (-> {:request {:multipart-params multipart-params}}
      enter
      (get-in [:request :body-params])))

(deftest merges-a-file-part-into-the-edn-fields-test
  (let [avatar (upload "avatar.png")]
    (is (match? {:title  "hello"
                 :avatar avatar}
                (body-params-after {sut/params-part "{:title \"hello\"}"
                                    "avatar"        avatar})))))

(deftest keeps-the-edn-fields-typed-test
  (is (match? {:count 3
               :tags  ["a" "b"]
               :ok    true}
              (body-params-after
                {sut/params-part "{:count 3 :tags [\"a\" \"b\"] :ok true}"
                 "avatar"        (upload "avatar.png")}))))

(deftest collects-a-multiple-file-control-as-a-vector-test
  (let [files [(upload "one.png") (upload "two.png")]]
    (is (match? {:photos files}
                (body-params-after {sut/params-part "{}"
                                    "photos"        files})))))

(deftest ignores-non-file-parts-alongside-the-edn-part-test
  (testing "a stray text part does not become a body param"
    (is (match? {:title "hello"}
                (body-params-after {sut/params-part "{:title \"hello\"}"
                                    "stray"         "text"})))
    (is (nil? (:stray (body-params-after {sut/params-part "{:title \"hello\"}"
                                          "stray"         "text"}))))))

(deftest leaves-a-plain-multipart-request-alone-test
  (testing "without the edn part, reitit's :parameters :multipart path stands"
    (is (nil? (body-params-after {"avatar" (upload "avatar.png")})))))

(deftest leaves-a-request-with-no-multipart-params-alone-test
  (is (nil? (body-params-after nil))))

(deftest rhizome-multipart-runs-after-multipart-test
  (is (= [:tram/multipart] (:must-run-after sut/rhizome-multipart-interceptor)))
  (is (< (.indexOf (mapv :name (sut/wire-format)) :tram/multipart)
         (.indexOf (mapv :name (sut/wire-format)) :tram/rhizome-multipart)
         (.indexOf (mapv :name (sut/wire-format)) :tram/coerce-request))))
