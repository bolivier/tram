(ns test-migrations.seed-data
  (:require [camel-snake-kebab.core :as csk]
            [methodical.core :as m]
            [toucan2.core :as t2]
            [toucan2.jdbc.options :as jdbc.options]
            [toucan2.pipeline :as pipeline]
            [tram.core :as tram]))

;; This is for doing default kebab-case transforms of our model keys.
(m/defmethod pipeline/transduce-query :around
  [:default :default :default]
  [rf query-type model parsed-args resolved-query]
  (binding [jdbc.options/*options* (assoc jdbc.options/*options*
                                     :label-fn csk/->kebab-case)]
    (next-method rf query-type model parsed-args resolved-query)))

(m/defmethod t2/do-with-connection :default
  [_connectable f]
  (t2/do-with-connection (tram.core/get-database-config "test") f))



(defn seed-up [_config]
  (t2/insert! :models/accounts {}))

(defn seed-down [_config]
  (t2/delete! :models/accounts))
