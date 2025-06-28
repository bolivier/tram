(ns tram.utils)

(defn map-keys [f coll]
  (reduce-kv (fn [acc k v] (assoc acc (f k) v)) {} coll))

(defn map-vals [f coll]
  (reduce-kv (fn [acc k v] (assoc acc k (f v))) {} coll))
