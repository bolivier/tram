(ns tram.sse-test
  (:require [clojure.test :refer [deftest is testing]]
            [matcher-combinators.test]
            [reitit.core :as r]
            [test-app.handlers.authentication-handlers :refer [routes]]
            [tram.routes :as tr]
            [tram.sse :as sut]))

(def rendering-router
  "A router with the render concern-group, which is what a stream event goes
  out through."
  (tr/tram-router routes
                  {:data {:interceptors [(tr/format-interceptor)
                                         (tr/render {:page-wrapper
                                                     (fn [body] [:html
                                                                 body])})]}}))

(defrecord RecordingEmitter [sent open?]
  sut/EventEmitter
  (send-event! [_ event]
    (when @open?
      (swap! sent conj
        (sut/frame event))
      true))
  (send-comment! [_ text]
    (swap! sent conj
      (sut/comment-frame text))
    true)
  (close-stream! [_]
    (reset! open? false)))

(defn- recording-emitter []
  (->RecordingEmitter (atom []) (atom true)))

(deftest frames-an-event-test
  (is
    (=
      "event: dom/morph\ndata: #:dom{:content \"<li id=\\\"row-4\\\">x</li>\"}\n\n"
      (sut/frame (sut/morph "<li id=\"row-4\">x</li>")))))

(deftest frames-a-comment-test
  (is (= ": keep-alive\n\n" (sut/comment-frame "keep-alive"))))

(deftest event-constructors-test
  (is (match? {:command     :dom/morph
               :dom/content [:li#row-4 "imported"]}
              (sut/morph [:li#row-4 "imported"])))
  (is (match? {:command :dom/remove
               :dom/id  "row-4"}
              (sut/remove-element "row-4")))
  (is (match? {:command  :dom/morph
               :locals   {:row 1}
               :template :view/import-row}
              (sut/render :view/import-row {:row 1}))))

(deftest drains-a-seq-source-test
  (let [emitter (recording-emitter)]
    (sut/drain-stream! [(sut/morph "<p id=\"a\">a</p>")
                        (sut/remove-element "b")]
                       emitter
                       identity)
    (is (= 2 (count @(:sent emitter))))
    (is (false? @(:open? emitter))
        "the stream closes when the source is spent")))

(deftest stops-draining-once-the-client-has-gone-test
  (let [emitter (recording-emitter)]
    (reset! (:open? emitter) false)
    (sut/drain-stream! (repeat 100 (sut/morph "<p id=\"a\">a</p>"))
                       emitter
                       identity)
    (is (= [] @(:sent emitter)))))

(deftest drops-one-unrenderable-event-and-keeps-going-test
  (let [emitter (recording-emitter)
        render  (fn [event]
                  (when-not (= "bad" (:dom/content event))
                    event))]
    (sut/drain-stream! [(sut/morph "bad") (sut/morph "<p id=\"a\">a</p>")]
                       emitter
                       render)
    (is (= 1 (count @(:sent emitter))))))

(defn- opening-ctx [route]
  (let [match (r/match-by-name rendering-router route)]
    {:request {:uri            (:path match)
               :request-method :get
               :headers        {"rhizome-request" "true"}
               ::r/match       match
               ::r/router      rendering-router}}))

(deftest renders-hiccup-content-to-html-test
  (let [render (sut/event-renderer (opening-ctx :route/sign-in))]
    (is (= "<li id=\"row-4\">imported</li>"
           (:dom/content (render (sut/morph [:li#row-4 "imported"])))))))

(deftest renders-a-named-view-test
  (let [render (sut/event-renderer (opening-ctx :route/forgot-password))]
    (testing "an event naming a template resolves it the way a handler does"
      (is (string? (:dom/content (render (sut/render :view/forgot-password
                                                     nil))))))))

(deftest leaves-a-contentless-event-alone-test
  (let [render (sut/event-renderer (opening-ctx :route/sign-in))]
    (is (match? {:command :dom/remove
                 :dom/id  "row-4"}
                (render (sut/remove-element "row-4"))))))
