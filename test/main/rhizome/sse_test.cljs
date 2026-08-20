(ns rhizome.sse-test
  (:require [cljs.test :refer [deftest is]]
            [rhizome.sse :as sut]))

(deftest split-frames-keeps-an-incomplete-frame-back-test
  (is (= [["event: dom/morph"] "data: {"]
         (sut/split-frames "event: dom/morph\n\ndata: {"))))

(deftest split-frames-reads-several-frames-from-one-chunk-test
  (is (= [["a" "b" "c"] ""] (sut/split-frames "a\n\nb\n\nc\n\n"))))

(deftest split-frames-handles-crlf-test
  (is (= [["a"] "b"] (sut/split-frames "a\r\n\r\nb"))))

(deftest parse-frame-reads-event-and-data-test
  (is (=
        {:data  ["{:dom/content \"<p id=\\\"a\\\">a</p>\"}"]
         :event "dom/morph"}
        (sut/parse-frame
          "event: dom/morph\ndata: {:dom/content \"<p id=\\\"a\\\">a</p>\"}"))))

(deftest parse-frame-joins-several-data-lines-test
  (is (= {:data  ["one" "two"]
          :event "app/thing"}
         (sut/parse-frame "event: app/thing\ndata: one\ndata: two"))))

(deftest parse-frame-drops-comments-and-unknown-fields-test
  (is (= {} (sut/parse-frame ": keep-alive")))
  (is (= {:data  ["{}"]
          :event "dom/morph"}
         (sut/parse-frame "id: 4\nretry: 1000\nevent: dom/morph\ndata: {}"))))

(deftest parse-frame-takes-the-value-without-its-leading-space-test
  (is (= {:event "dom/morph"} (sut/parse-frame "event:dom/morph")))
  (is (= {:data ["  padded"]} (sut/parse-frame "data:   padded"))))

(deftest frame->command-names-a-command-test
  (is (= {:do :dom/morph
          :dom/content "<p id=\"a\">a</p>"}
         (sut/frame->command {:data ["{:dom/content \"<p id=\\\"a\\\">a</p>\"}"]
                              :event "dom/morph"}))))

(deftest frame->command-ignores-a-frame-with-nothing-to-run-test
  (is (nil? (sut/frame->command {:data ["{}"]})))
  (is (nil? (sut/frame->command {:event "dom/morph"}))))
