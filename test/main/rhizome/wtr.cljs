(ns rhizome.wtr
  "Entry point for the @web/test-runner browser harness.

  shadow's built-in `shadow.test.browser/start` runs tests but returns nothing,
  so it can't feed web-test-runner's `sessionFinished({passed})`. This runner
  instead resolves a JS Promise from cljs.test's `:end-run-tests` summary (which
  fires after async tests complete), so the harness gets a real pass/fail result.

  Test namespaces must be required here so shadow compiles them into this build
  and their `deftest`s register with shadow.test.env."
  (:require [cljs.test :as ct]
            rhizome.dom-test
            [rhizome.fake-server :as fake-server]
            rhizome.mount-test
            rhizome.signals-test
            rhizome.smoke-test
            rhizome.sse-test
            [shadow.test :as st]
            [shadow.test.env :as env]))

(defn init []
  ;; mount point some DOM tests may rely on; harmless if unused
  (when-not (.getElementById js/document
                             "test-root")
    (let [d (.createElement js/document
                            "div")]
      (set! (.-id d)
            "test-root")
      (.appendChild (.-body js/document)
                    d))))

(defn- var->name [v]
  (let [{:keys [ns name]} (meta v)]
    (str ns "/" name)))

(defn start []
  ;; (re)register all compiled tests, then run with a reporter that
  ;; resolves once the whole run (incl. async tests) is done.
  ;; web-test-runner expects `testResults` as a suite tree: {passed,
  ;; suites:[], tests:[{name,passed,error?}]}.
  (-> (env/get-test-data)
      (env/reset-test-data!))
  (.then
    (fake-server/start)
    (fn []
      (js/Promise.
        (fn [resolve _reject]
          (let [orig    ct/report
                tests   (atom [])
                current (atom nil)]
            (st/run-all-tests
              (assoc (ct/empty-env)
                :report-fn
                (fn [m]
                  ;; delegate to the default so counters increment +
                  ;; console prints
                  (orig m)
                  (case (:type m)
                    :begin-test-var (reset! current {:name   (var->name (:var
                                                                          m))
                                                     :passed true})
                    (:fail :error)  (when @current
                                      (swap! current assoc
                                        :passed false
                                        :error  #js {:message  (str
                                                                 (or (:message
                                                                       m)
                                                                     (:type m)))
                                                     :expected (pr-str
                                                                 (:expected m))
                                                     :actual   (pr-str (:actual
                                                                         m))}))
                    :end-test-var   (do (when @current
                                              (swap! tests conj
                                                @current))
                                        (reset! current
                                          nil))
                    :end-run-tests  (resolve
                                      (clj->js
                                        {:passed (and (zero? (or (:fail m) 0))
                                                      (zero? (or (:error m) 0)))
                                         :suites []
                                         :tests  @tests}))
                    nil))))))))))
