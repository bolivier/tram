(ns hooks.toucan2.common
  (:require
   [clj-kondo.hooks-api :as hooks]))

(defn pre-post?
  "Whether this node is a pre/post assertion map at the start of a function body."
  [node]
  (when (hooks/map-node? node)
    (let [m (hooks/sexpr node)]
      ((some-fn :pre :post) m))))

(defn splice-into-body
  "Splice node(s) into the beginning on `body`, splicing after `:pre`/`:post` assertion maps if needed. "
  [body & nodes]
  (if (pre-post? (first body))
    (concat
     [(first body)]
     nodes
     (rest body))
    (concat
     nodes
     body)))
