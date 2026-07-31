(ns rhizome.macros
  (:require [cljs.test]))

(defmethod cljs.test/assert-expr 'eventually
  [_menv msg form]
  (let [[_
         pred
         &
         {:keys [timeout interval]
          :or   {timeout  2000
                 interval 50}}]
        form]
    `(js/Promise. (fn [resolve#]
                    (let [deadline# (+ (js/Date.now) ~timeout)]
                      (letfn [(check# []
                                (if (or (try
                                          ~pred
                                          (catch :default _#
                                            false))
                                        (>= (js/Date.now)
                                            deadline#))
                                  (resolve# (cljs.test/is ~pred
                                                          ~msg))
                                  (js/setTimeout check#
                                                 ~interval)))]
                        (check#)))))))

(defmacro with-html
  [root-binding & body]
  `(let [hiccup# ~(second root-binding)
         root#   (rhizome.test-utils/create-html-root hiccup#)
         ~(first root-binding) root#]
     (try
       ~@body
       (finally (.remove (.-parentElement root#))))))
