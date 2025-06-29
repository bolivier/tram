(ns tram.associations
  (:require [clojure.string :as str]
            [declensia.core :as dc]
            [methodical.core :as m]
            [toucan2.core :as t2]
            [tram.utils.language :as lang]))

(def ^:dynamic *relationships*
  (atom {}))

(defn has-many! [model-with-many model-of-many & {:keys [through]}]
  (swap! *relationships* (fn [relationships]
                           (update-in relationships
                                      [model-with-many :has-many]
                                      assoc
                                      model-of-many
                                      {:through through}))))
(defn belongs-to! [owner ownee]
  (swap! *relationships* (fn [relationships]
                           (-> relationships
                               (update-in [owner :belongs-to] set)
                               (update-in [owner :belongs-to] conj ownee)))))

(m/defmethod t2/model-for-automagic-hydration [:default :default]
  [model-to-hydrate hydration-id]
  (when-not (or (contains? (get-in @*relationships*
                                   [model-to-hydrate :has-many])
                           hydration-id)
                (contains? (get-in @*relationships*
                                   [model-to-hydrate :belongs-to])
                           hydration-id))
    (keyword "model"
             (dc/pluralize (name hydration-id)))))

(m/defmethod t2/simple-hydrate [:default :default]
  [model k instance]
  (cond
    (contains? (get-in @*relationships* [model :has-many]) (lang/modelize k))
    (let [join-table      (keyword (str/join "-"
                                             (reverse (sort [(name model)
                                                             (name k)]))))
          fk-for-instance (lang/table-name->foreign-key-id model)
          fk-for-other    (lang/table-name->foreign-key-id k)
          join-clause     [join-table
                           [:=
                            (keyword (str (name k) ".id"))
                            (keyword
                              (str (name join-table) "." (name fk-for-other)))]]
          where-clause    [:=
                           (keyword
                             (str (name join-table) "." (name fk-for-instance)))
                           (:id instance)]]
      (assoc instance
        k
        (t2/select (keyword "model" (name k))
                   {:join  join-clause
                    :where where-clause})))

    (contains? (get-in @*relationships* [model :belongs-to]) (lang/modelize k))
    (t2/select (keyword "model" (name k))
               (keyword (lang/table-name->foreign-key-id (name model)))
               (:id instance))))
