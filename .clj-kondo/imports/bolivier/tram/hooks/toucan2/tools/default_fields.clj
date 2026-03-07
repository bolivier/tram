(ns hooks.toucan2.tools.default-fields
  (:require
   [clj-kondo.hooks-api :as hooks]))

(defn define-default-fields [context]
  (letfn [(update-node [node]
            (let [[_ model & body] (:children node)]
              (hooks/list-node
               (list*
                (hooks/token-node `let)
                (hooks/vector-node [(hooks/token-node '&model) model])
                ;; make it appear used
                (hooks/token-node '&model)
                body))))]
    (update context :node update-node)))
