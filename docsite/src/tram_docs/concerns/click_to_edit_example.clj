(ns tram-docs.concerns.click-to-edit-example)

(defonce patron
  (atom {:name "Rick Blaine"
         :role "Café Américain, proprietor"}))

(defn current []
  @patron)

(defn update-patron! [{:keys [name role]}]
  (swap! patron assoc
    :name name
    :role role))
