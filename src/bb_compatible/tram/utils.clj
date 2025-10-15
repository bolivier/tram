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

(defn first-where [pred coll]
  (some (fn [elm]
          (when (pred elm)
            elm))
        coll))

(defn index-by
  "Index a seq of elements by the value of `(f element)`.

  Returns a map of the elements of `coll` keyed by `f`. Throws an error if the
  seq is not indexable ie. if there are 2 values in the seq for which (f x) are
  equal."
  [f coll]
  (reduce
    (fn [acc elm]
      (let [k (f elm)]
        (when (contains? acc
                         k)
          (throw
            (ex-info
              "Indexed coll with non-indexable elements (duplicate key generated)."
              {:coll           coll
               :duplicated-key k
               :element        elm})))
        (assoc acc k elm)))
    {}
    coll))
