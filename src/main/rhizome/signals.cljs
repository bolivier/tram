(ns rhizome.signals)

(def registry
  "Every live signal, keyed by name, with its current value and listeners."
  (atom {}))

(defn ->registry-entry
  "A registry entry for signal `key` holding `value` and no listeners."
  [key value]
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

(defn value
  "The current value of signal `key`, or nil when it has none."
  [key]
  (get-in @registry [key :value]))

(defn put!
  "Sets signal `key` to `value` and calls each of its listeners with `value`."
  [key value]
  (swap! registry (fn [r] (assoc-in (ensure-entry r key) [key :value] value)))
  (doseq [cb (get-in @registry [key :listeners])]
    (cb value)))

(defn retire!
  "Removes signal `key` from the registry, along with its listeners."
  [key]
  (swap! registry dissoc
    key))

(defn listen!
  "Calls `cb` with each new value of signal `key`, starting with the current one."
  [key cb]
  (swap! registry (fn [r]
                    (update-in (ensure-entry r key) [key :listeners] conj cb)))
  (cb (get-in @registry [key :value])))

(comment
  @registry
  (put! :example-text "hello world")
  nil)
