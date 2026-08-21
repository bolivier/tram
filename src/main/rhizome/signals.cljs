(ns rhizome.signals)

(def registry
  (atom {}))

(defn ->registry-entry [key value]
  {:key       key
   :value     value
   :listeners []})

(defn- ensure-entry
  "Adds an empty entry for `key` when nothing has claimed it yet.

  A listener can mount before the element that owns the signal, because mount
  order is registry order and not document order."
  [registry key]
  (if (contains? registry
                 key)
    registry
    (assoc registry
      key (->registry-entry key
                            nil))))

(defn value [key]
  (get-in @registry [key :value]))

(defn put! [key value]
  (swap! registry (fn [r] (assoc-in (ensure-entry r key) [key :value] value)))
  (doseq [cb (get-in @registry [key :listeners])]
    (cb value)))

(defn retire! [key]
  (swap! registry dissoc
    key))

(defn listen! [key cb]
  (swap! registry (fn [r]
                    (update-in (ensure-entry r key) [key :listeners] conj cb)))
  (cb (get-in @registry [key :value])))

(comment
  @registry
  (put! :example-text "hello world")
  nil)
