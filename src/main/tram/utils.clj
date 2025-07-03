(ns tram.utils)

(defn map-keys [f coll]
  (reduce-kv (fn [acc k v] (assoc acc (f k) v)) {} coll))

(defn map-vals [f coll]
  (reduce-kv (fn [acc k v] (assoc acc k (f v))) {} coll))

(defmacro with-same-output
  "Executes body with var bound to the string representation of value.
   Keywords are converted to strings (name only) before body execution.
   The result is returned in the same type as the input (string or keyword).
   For keywords, preserves the original namespace."
  [[var value] & body]
  `(let [original-value# ~value
         original-type#  (cond
                           (string? original-value#) :string
                           (keyword? original-value#) :keyword
                           :else
                           (throw (ex-info "Value must be string or keyword"
                                           {:value original-value#})))
         original-ns#    (when (keyword? original-value#)
                           (namespace original-value#))
         string-value#   (if (keyword? original-value#)
                           (name original-value#)
                           original-value#)
         ~var            string-value#
         result#         (do ~@body)]
     (case original-type#
       :string  (str result#)
       :keyword (if original-ns#
                  (keyword original-ns#
                           (str result#))
                  (keyword (str result#))))))

(defn evolve
  "Evolves `coll` with the evolving functions in `evolutions`.

  `coll` should be a map, and `evolutions` a map of keys to functions of one
  argument that will be called on matching keys in `coll`. "
  [evolutions coll]
  (let [relevant-evolutions (select-keys evolutions (keys coll))]
    (merge-with (fn [value f] (f value)) coll relevant-evolutions)))
