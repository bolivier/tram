(ns tram-docs.concerns.inline-validation-example
  (:require [clojure.string :as str]))

(def only-flight-out
  "Lisbon")

(defn errors
  "Field errors for a letter-of-transit application, keyed by field name.

  An empty map means the application is good."
  [application]
  (let [applicant   (str/trim (or (:applicant application) ""))
        destination (str/trim (or (:destination application) ""))]
    (cond-> {}
      (str/blank? applicant) (assoc :applicant
                               "Everyone in Casablanca needs a name.")
      (str/blank? destination) (assoc :destination "Name a destination.")
      (and (seq destination)
           (not= (str/lower-case only-flight-out) (str/lower-case destination)))
      (assoc :destination
        (str "The only plane out of Casablanca goes to "
             only-flight-out
             ".")))))
