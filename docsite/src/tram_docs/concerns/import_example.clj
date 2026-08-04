(ns tram-docs.concerns.import-example)

(def clearance-delay-ms
  "Slow enough that the rows land one at a time. A stream that finishes before
  the first paint shows nothing a plain response could not."
  500)

(def manifest
  [{:id    1
    :label "Ugarte, Signor"}
   {:id    2
    :label "Laszlo, Victor"}
   {:id    3
    :label "Lund, Ilsa"}
   {:id    4
    :label "Berger, Norwegian"}
   {:id    5
    :label "Ferrari, Signor"}])

(defn clear-passenger!
  "Clears one passenger, slowly, and says what happened to them."
  [passenger]
  (Thread/sleep clearance-delay-ms)
  (assoc passenger
    :status (if (= 1 (:id passenger))
              "detained"
              "cleared")))
