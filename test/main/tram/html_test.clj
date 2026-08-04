(ns tram.html-test
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test]
            [muuntaja.format.core :as mfc]
            [rhizome.html :as h]
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

(deftest encode-rhizome-attributes-test
  (binding [*req* {:reitit.core/router sample-router}]
    (let
      [encoder (sut/huff-html-encoder nil)
       hiccup [:button {:rhizome.core/click {:http/url :route/dashboard}}]
       expected
       "<button rhizome_core___click=\"{:http/url &quot;/dashboard&quot;}\"></button>"

       output (String. (mfc/encode-to-bytes encoder hiccup "UTF-8"))]
      (is (= expected output)))))

(deftest encode-rhizome-submit-attribute-test
  (binding [*req* {:reitit.core/router sample-router}]
    (let
      [encoder (sut/huff-html-encoder nil)
       hiccup [:form {:rhizome.core/submit {:http/url :route/dashboard}}]
       expected
       "<form rhizome_core___submit=\"{:http/url &quot;/dashboard&quot;}\"></form>"

       output (String. (mfc/encode-to-bytes encoder hiccup "UTF-8"))]
      (is (= expected output)))))


(def ^:private entity->char
  {"&quot;" "\""
   "&#39;"  "'"
   "&lt;"   "<"
   "&gt;"   ">"
   "&amp;"  "&"})

(defn- decode-entities
  "What a browser hands back from `getAttribute`, so a test can read the command
  map the rhizome runtime would see."
  [s]
  (reduce-kv str/replace s entity->char))

(deftest rhizome-command-attribute-survives-the-browser-test
  (let [command   {:op          :dom/morph
                   :on          :event/click
                   :http/url    "/counter"
                   :dom/content "say \"hi\""
                   :ident       [:closest "[data-panel]"]}
        rendered  (str (h/html [:button {:rhizome.core/click command}]))
        attribute (second (re-find #"rhizome_core___click=\"([^\"]*)\""
                                   rendered))]
    (is (not (str/includes? attribute "\""))
        "a raw quote ends the attribute early, truncating the command")
    (is (= command (edn/read-string (decode-entities attribute))))))
