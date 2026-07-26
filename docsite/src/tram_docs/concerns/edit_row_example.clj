(ns tram-docs.concerns.edit-row-example)

(def store
  (atom {1 {:id    1
            :name  "Michael Scott"
            :email "mscott@dundermifflin.test"}
         2 {:id    2
            :name  "Dwight Schrute"
            :email "schrutefarms@dundermifflin.test"}
         3 {:id    3
            :name  "Jan Levinson"
            :email "jan@dundermifflin.test"}}))

(defn get-people []
  (sort-by :id (vals @store)))

(defn update-person [id {:keys [name email]}]
  (swap! store (fn [ppl]
                 (-> ppl
                     (assoc-in [id :name] name)
                     (assoc-in [id :email] email)))))
