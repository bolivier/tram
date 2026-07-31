(ns rhizome.smoke-test
  "Proves the harness end to end: dom setup via with-html, an async
  assertion via eventually, and an http round trip through msw."
  (:require [cljs.test :refer [async deftest is]]
            [promesa.core :as p]
            [rhizome.fake-server :as fake-server]
            [rhizome.test-utils :as tu])
  (:require-macros [rhizome.macros :refer [with-html]]))

(deftest with-html-mounts-and-cleans-up-test
  (with-html [root [:div#smoke-root [:span "hello"]]]
             (is (= "smoke-root" (.-id root)))
             (is (= "hello" (.-textContent root)))))

(deftest fake-server-round-trip-test
  (fake-server/use-handlers fake-server/server
                            (fake-server/make-handlers
                              "/smoke"
                              {:get (fn [_req] {:body [:p#pong "pong"]})}))
  (let [result (atom nil)]
    (-> (js/fetch "/smoke")
        (.then #(.text %))
        (.then #(reset! result %)))
    (async done
           (p/do (is (eventually (= "<p id=\"pong\">pong</p>" @result)))
                 (done)))))

(deftest fire!-dispatches-test
  (with-html [root [:button#clicker "go"]]
             (let [clicks (atom 0)]
               (.addEventListener root
                                  "click"
                                  #(swap! clicks inc))
               (tu/fire! root "click")
               (is (= 1 @clicks)))))
