(ns rhizome.signals)

(def registry
  (atom {}))

(defn ->registry-entry [key value]
  {:key       key
   :value     value
   :listeners []})

(defn register! [key value]
  (swap! registry assoc
    key
    (->registry-entry key value)))

(defn retire! [key]
  (swap! registry dissoc
    key))

(defn put! [key value]
  (when-let [entry (get @registry key)]
    (swap! registry assoc-in
      [key :value]
      value)
    (when-let [listener-cbs (:listeners entry)]
      (doseq [cb listener-cbs]
        (cb value)))))

(defn listen! [key cb]
  (when-let [entry (get @registry key)]
    (cb (:value entry))
    (swap! registry update-in
      [key :listeners]
      conj
      cb)))

(comment
  @registry
  (put! :example-text "hello world")
  nil)
