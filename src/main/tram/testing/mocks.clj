(ns tram.testing.mocks)

(def ^:dynamic *calls*
  (atom nil))

(defmacro with-stub
  [[func opts] & body]
  (let [return-value (:returns opts)
        return-fn    (:return-fn opts)
        args         (gensym)]
    `(let [return-value# ~return-value
           return-fn#    ~return-fn]
       (with-redefs [*calls* (if @*calls*
                               *calls*
                               (atom []))
                     ~func   (fn [& ~args]
                               (swap! *calls* conj
                                 {:func ~func
                                  :args ~args})
                               (cond
                                 ~(some? return-value) return-value#
                                 ~(some? return-fn)    (return-fn# ~args)
                                 :else                 nil))]
         ~@body))))
