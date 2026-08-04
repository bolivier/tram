(ns tram-docs.concerns.click-to-load-example)

(def page-size
  3)

(def waiting-list
  "Everyone in Casablanca waiting on an exit visa to Lisbon."
  [{:name        "Victor Laszlo"
    :nationality "Czech"
    :papers      "none"}
   {:name        "Ilsa Lund"
    :nationality "Norwegian"
    :papers      "none"}
   {:name        "Ugarte"
    :nationality "stateless"
    :papers      "forged"}
   {:name        "Annina Brandel"
    :nationality "Bulgarian"
    :papers      "denied"}
   {:name        "Jan Brandel"
    :nationality "Bulgarian"
    :papers      "denied"}
   {:name        "Berger"
    :nationality "Norwegian"
    :papers      "none"}
   {:name        "Yvonne"
    :nationality "French"
    :papers      "pending"}
   {:name        "Carl"
    :nationality "Austrian"
    :papers      "pending"}
   {:name        "Sascha"
    :nationality "Russian"
    :papers      "none"}
   {:name        "Emil"
    :nationality "French"
    :papers      "none"}
   {:name        "Abdul"
    :nationality "Moroccan"
    :papers      "none"}
   {:name        "Signor Ferrari"
    :nationality "Italian"
    :papers      "not leaving"}])

(defn queue
  "The first `loaded` names on the list."
  [loaded]
  (take loaded waiting-list))

(defn next-page
  "How many names the next click should show, or nil at the end of the list."
  [loaded]
  (when (< loaded
           (count waiting-list))
    (+ loaded
       page-size)))
