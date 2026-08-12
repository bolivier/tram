(ns rhizome.directives-test
  (:require [cljs.test :refer [async deftest is]]
            [promesa.core :as p]
            [rhizome.directives :as sut]
            [rhizome.dom :as dom]
            [rhizome.fake-server :as fake-server])
  (:require-macros [rhizome.macros]))

(deftest dom-navigate-visits-its-url-test
  (let [visited  (atom nil)
        original dom/visit!]
    (set! dom/visit!
          (fn [url]
            (reset! visited url)))
    (try
      (sut/execute {:do      :dom/navigate
                    :dom/url "/somewhere"})
      (is (= "/somewhere" @visited))
      (finally (set! dom/visit! original)))))

(deftest a-post-carries-the-csrf-token-from-the-meta-tag-test
  (let [meta-el (js/document.createElement "meta")
        seen    (atom nil)]
    (.setAttribute meta-el "name" "csrf-token")
    (.setAttribute meta-el "content" "tok-123")
    (.appendChild js/document.head meta-el)
    (fake-server/use-handlers
      fake-server/server
      (fake-server/make-handlers
        "/directives-test/csrf"
        {:post (fn [req]
                 (reset! seen (get-in req [:headers "x-csrf-token"]))
                 {:body [:p "ok"]})}))
    (sut/execute {:do       :http/post
                  :http/url "/directives-test/csrf"})
    (async done
           (p/do (is (eventually (= "tok-123" @seen)))
                 (.remove meta-el)
                 (done)))))

(deftest a-get-carries-no-csrf-token-test
  (let [meta-el (js/document.createElement "meta")
        seen    (atom ::pending)]
    (.setAttribute meta-el "name" "csrf-token")
    (.setAttribute meta-el "content" "tok-123")
    (.appendChild js/document.head meta-el)
    (fake-server/use-handlers
      fake-server/server
      (fake-server/make-handlers
        "/directives-test/csrf-get"
        {:get (fn [req]
                (reset! seen (get-in req [:headers "x-csrf-token"]))
                {:body [:p "ok"]})}))
    (sut/execute {:do       :http/get
                  :http/url "/directives-test/csrf-get"})
    (async done
           (p/do (is (eventually (nil? @seen)))
                 (.remove meta-el)
                 (done)))))

(deftest a-followed-redirect-navigates-instead-of-morphing-test
  (fake-server/use-handlers
    fake-server/server
    (concat (fake-server/make-handlers
              "/directives-test/go"
              {:post (fn [_req]
                       {:body    [:p "redirecting"]
                        :headers {"Location" "/directives-test/landed"}
                        :status  303})})
            (fake-server/make-handlers
              "/directives-test/landed"
              {:get (fn [_req] {:body [:h1#landed "landed"]})})))
  (let [visited  (atom nil)
        original dom/visit!]
    (set! dom/visit!
          (fn [url]
            (reset! visited url)))
    (sut/execute {:do       :http/post
                  :http/url "/directives-test/go"})
    (async done
           (p/do (is (eventually (some-> @visited
                                         (.endsWith
                                           "/directives-test/landed"))))
                 (set! dom/visit!
                       original)
                 (done)))))
