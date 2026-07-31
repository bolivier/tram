(ns rhizome.test-utils
  (:require ["sinon" :as sinon]
            [rhizome.html :as h])
  (:require-macros [rhizome.macros]))

(defn create-html-root [hiccup]
  (let [template (.createElement js/document "section")
        html     (h/html hiccup)]
    (set! (.-innerHTML template) html)
    (.appendChild (.-body js/document) template)
    (.-firstElementChild template)))

(defn fire-key! [el k]
  (.dispatchEvent el
                  (js/KeyboardEvent. "keydown"
                                     #js {:key     k
                                          :bubbles true})))

(defn fire! [el event-type]
  (.dispatchEvent el (js/Event. event-type #js {:bubbles true})))

(defn fake-timers
  "Installs sinon fake timers scoped to *only* the debounce timer. msw's
  service-worker round-trip keeps using real timers, so requests still
  resolve; we just get deterministic control over when the debounce window
  elapses. Call `.tick` to advance, `.restore` to uninstall."
  []
  (sinon/useFakeTimers #js {:toFake #js ["setTimeout" "clearTimeout"]}))

(defn type!
  "Simulate a user typing `text` into `el`: append to its value and fire an
  `input` event, the way a real keystroke would."
  [el text]
  (set! (.-value el) (str (.-value el) text))
  (fire! el "input"))
