(ns tram.csrf-test
  (:require [buddy.core.nonce :as nonce]
            [clojure.test :refer [deftest is testing]]
            [tram.csrf :as sut]
            [tram.vars :refer [*req*]]))

(def test-secret
  (buddy.core.codecs/bytes->hex (nonce/random-bytes 32)))

(deftest token-generation
  (let [token (sut/generate-token)]
    (is (string? token))
    (is (= 64 (count token)) "32 bytes = 64 hex chars")
    (is (re-matches #"[0-9a-f]+" token) "hex characters only")))

(deftest sign-unsign-roundtrip
  (let [token  (sut/generate-token)
        signed (sut/sign-token test-secret token)]
    (is (= token (sut/unsign-token test-secret signed)))))

(deftest tampered-signature-rejected
  (let [token  (sut/generate-token)
        signed (sut/sign-token test-secret token)]
    (is (nil? (sut/unsign-token test-secret (str signed "tampered"))))))

(deftest different-secret-rejected
  (let [token        (sut/generate-token)
        other-secret (buddy.core.codecs/bytes->hex (nonce/random-bytes 32))
        signed       (sut/sign-token test-secret token)]
    (is (nil? (sut/unsign-token other-secret signed)))))

(defn- make-interceptor []
  (sut/csrf-interceptor test-secret))

(defn- enter [ctx]
  ((:enter (make-interceptor)) ctx))

(defn- leave [ctx]
  ((:leave (make-interceptor)) ctx))

(deftest get-request-generates-token
  (let [ctx    {:request {:request-method :get
                          :headers        {}}}
        result (enter ctx)]
    (is (string? (get-in result [:request :csrf-token])))
    (is (= 64 (count (get-in result [:request :csrf-token]))))))

(deftest get-request-reuses-cookie-token
  (let [token  (sut/generate-token)
        signed (sut/sign-token test-secret token)
        ctx    {:request {:request-method :get
                          :headers        {"cookie" (str "__csrf-token="
                                                         signed)}}}
        result (enter ctx)]
    (is (= token (get-in result [:request :csrf-token])))))

(deftest post-without-token-returns-403
  (let [token  (sut/generate-token)
        signed (sut/sign-token test-secret token)
        ctx    {:request {:request-method :post
                          :headers        {"cookie" (str "__csrf-token="
                                                         signed)}}}
        result (enter ctx)]
    (is (= 403 (get-in result [:response :status])))))

(deftest post-with-valid-form-token-passes
  (let [token  (sut/generate-token)
        signed (sut/sign-token test-secret token)
        ctx    {:request {:request-method :post
                          :headers        {"cookie" (str "__csrf-token="
                                                         signed)}
                          :form-params    {"__anti-forgery-token" token}}}
        result (enter ctx)]
    (is (nil? (:response result)) "no early response means pass")
    (is (= token (get-in result [:request :csrf-token])))))

(deftest post-with-valid-header-token-passes
  (let [token  (sut/generate-token)
        signed (sut/sign-token test-secret token)
        ctx    {:request {:request-method :post
                          :headers        {"cookie"       (str "__csrf-token="
                                                               signed)
                                           "x-csrf-token" token}}}
        result (enter ctx)]
    (is (nil? (:response result)) "no early response means pass")
    (is (= token (get-in result [:request :csrf-token])))))

(deftest post-with-mismatched-token-returns-403
  (let [token       (sut/generate-token)
        wrong-token (sut/generate-token)
        signed      (sut/sign-token test-secret token)
        ctx         {:request
                     {:request-method :post
                      :headers        {"cookie" (str "__csrf-token=" signed)}
                      :form-params    {"__anti-forgery-token" wrong-token}}}
        result      (enter ctx)]
    (is (= 403 (get-in result [:response :status])))))

(deftest route-with-csrf-false-skips-validation
  (let [ctx    {:request {:request-method    :post
                          :headers           {}
                          :reitit.core/match {:data {:csrf false}}}}
        result (enter ctx)]
    (is (nil? (:response result)) "no early response means skipped")
    (is (string? (get-in result [:request :csrf-token])))))

(deftest leave-sets-cookie
  (let [token  (sut/generate-token)
        ctx    {:request  {:csrf-token token}
                :response {:status 200
                           :body   "ok"}}
        result (leave ctx)]
    (is (string? (get-in result [:response :headers "Set-Cookie"])))
    (is (clojure.string/starts-with? (get-in result
                                             [:response :headers "Set-Cookie"])
                                     "__csrf-token="))))

(deftest leave-preserves-existing-set-cookie
  (let [token  (sut/generate-token)
        ctx    {:request  {:csrf-token token}
                :response {:status  200
                           :body    "ok"
                           :headers {"Set-Cookie" "session-id=123; Path=/"}}}
        result (leave ctx)]
    (is (vector? (get-in result [:response :headers "Set-Cookie"])))
    (is (= 2 (count (get-in result [:response :headers "Set-Cookie"]))))
    (is (= "session-id=123; Path=/"
           (first (get-in result [:response :headers "Set-Cookie"]))))))

(deftest csrf-hidden-field-produces-correct-hiccup
  (let [token "test-token-123"]
    (binding [*req* {:csrf-token token}]
      (is (= [:input {:type  "hidden"
                      :name  "__anti-forgery-token"
                      :value token}]
             (sut/csrf-hidden-field))))))

(deftest csrf-meta-tag-produces-correct-hiccup
  (let [token "test-token-456"]
    (binding [*req* {:csrf-token token}]
      (is (= [:meta {:name    "csrf-token"
                     :content token}]
             (sut/csrf-meta-tag))))))

(deftest put-and-delete-also-validated
  (testing "PUT requires CSRF token"
    (let [ctx    {:request {:request-method :put
                            :headers        {}}}
          result (enter ctx)]
      (is (= 403 (get-in result [:response :status])))))
  (testing "DELETE requires CSRF token"
    (let [ctx    {:request {:request-method :delete
                            :headers        {}}}
          result (enter ctx)]
      (is (= 403 (get-in result [:response :status])))))
  (testing "PATCH requires CSRF token"
    (let [ctx    {:request {:request-method :patch
                            :headers        {}}}
          result (enter ctx)]
      (is (= 403 (get-in result [:response :status]))))))
