(ns tram.associations
  "Associations are managed through the *associations* dynamic var. The keys of
  the map are models that have some asociations defined on them.

  The structure of these associations is a nested map with paths
  like this
  [model association-type hydrating-attribute association-entry]
  eg.
  {:models/users {:has-one {:address {:foreign-key :homeonwer-id
                                       :model      :models/users}}}}

  The entries contain data about how to find the model to hydrate at
  `hydrating-attribute`.

  Most commonly the keys are `:foreign-key`, `:table`, and `:model`.

  These can be overridden with the `opts` param."
  (:require [declensia.core :as dc]
            [methodical.core :as m]
            [toucan2.core :as t2]
            [toucan2.tools.hydrate :as t2.hydrate]
            [tram.language :as lang]))

(defonce ^:dynamic *associations*
  (atom {}))

(defn lookup-join-table [a b]
  (try
    (let [table-name (lang/join-table a b)]
      (t2/select-one (lang/join-table a b))
      table-name)
    (catch Exception e
      (if (re-find #"relation \".*\" does not exist"
                   (.getMessage e))
        nil
        (throw e)))))

(defn belongs-to!
  "Creates a belongs-to association.

  You should not need to use this fn unless you deviate from convention.

  `opts` are required.

  `:model`       - model to use for hydration
  `:foreign-key` - foreign key on `model` that you want to hydrate from with `attribute`"
  [model attribute opts]
  (swap! *associations* (fn [associations]
                          (assoc-in associations
                            [model :belongs-to attribute]
                            opts))))

(defn has-one!
  "Creates a has-one association.

  After calling, `model` can be passed to `tram.db/hydrate` along with
  `attribute` and a new key, `attribute`, will be added to the instance with the
  relevant model.

  For example, `(has-one! :model/users :address)` will allow you to
  write `(tram.db/hydrate user-instance :address)` and user-instance will get a
  new key `:address` with corresponding address model instance."
  ([model attribute]
   (has-one! model
             attribute
             {:model       (lang/modelize attribute)
              :foreign-key (lang/model->foreign-key model)}))
  ([model attribute opts]
   (swap! *associations* (fn [associations]
                           (assoc-in associations
                             [model :has-one attribute]
                             opts)))))

(defn has-many!
  "Creates a has-many association.

  After calling, `model` can be passed to `tram.db/hydrate` along with
  `attribute` and a new key, `attribute`, will be added to the instance with the
  relevant models.

  For example, `(has-many! :model/users :settings)` will allow you to
  write `(tram.db/hydrate user-instance :settings)` and user-instance will get a
  new key `:settings` with matching values.

  The database level relationship can either be:

  - corresponding attribute table has a foreign key to model instance
  - `model` and the model for `attribute` have a join table."
  ([model attribute]
   (has-many! model attribute {:foreign-key (lang/model->foreign-key model)}))
  ([owner attribute opts]
   (swap! *associations*
     (fn [associations]
       (let [attribute-model (lang/modelize attribute {:plural? false})
             entry (merge {:model      attribute-model
                           :join-table (lookup-join-table owner attribute)}
                          opts)]
         (assoc-in associations [owner :has-many attribute] entry))))))

(defn has-many? [model attribute]
  (some? (get-in @*associations* [model :has-many attribute :model])))

(defn has-one? [model attribute]
  (some? (get-in @*associations* [model :has-one attribute :model])))

(defn has-explicit-association? [model attribute]
  (or (has-one? model attribute) (has-many? model attribute)))

(m/defmethod t2/model-for-automagic-hydration [:default :default]
  [model attribute]
  (let [has-association (has-explicit-association? model attribute)
        alias-model     (or (get-in @*associations*
                                    [model :has-one attribute :model])
                            (get-in @*associations*
                                    [model :belongs-to attribute :model]))]
    (if has-association
      nil ;; handled in simple hydrate
      (or alias-model
          (lang/modelize attribute)))))

(defn qualified-column [table column]
  (keyword (str (name table) "." (name column))))

(m/defmethod t2.hydrate/fk-keys-for-automagic-hydration :default
  [original-model dest-key _hydrated-key]
  [(get-in @*associations*
           [original-model :belongs-to dest-key :foreign-key]
           (lang/name->foreign-key dest-key))])

(m/defmethod t2/simple-hydrate [:default :default]
  [model attribute instance]
  (let [join-table (get-in @*associations*
                           [model :has-many attribute :join-table])]
    (cond
      (some? join-table)
      (let [entry           (get-in @*associations* [model :has-many attribute])
            fk-for-instance (or (:foreign-key entry)
                                (lang/model->foreign-key model))
            fk-for-other    (or (:model-key entry)
                                (lang/model->foreign-key attribute))
            other-model     (:model entry)
            join-clause     [join-table
                             [:=
                              (qualified-column join-table fk-for-other)
                              (qualified-column other-model
                                                (first (t2/primary-keys
                                                         other-model)))]]
            where-clause    [:=
                             (qualified-column join-table fk-for-instance)
                             (:id instance)]]
        (assoc instance
          attribute
          (t2/select other-model
                     {:select (qualified-column other-model :*)
                      :join   join-clause
                      :where  where-clause})))

      (some? (get-in @*associations* [model :has-many attribute :model]))
      (let [entry (get-in @*associations* [model :has-many attribute])]
        (assoc instance
          attribute
          (t2/select (:model entry)
                     (or (:foreign-key entry) (lang/model->foreign-key model))
                     (get instance (first (t2/primary-keys model))))))

      (has-one? model attribute)
      (let [entry     (get-in @*associations* [model :has-one attribute])
            fk        (:foreign-key entry)
            one-model (:model entry)]
        (assoc instance
          attribute
          (t2/select-one one-model
                         fk
                         (get instance (first (t2/primary-keys model))))))

      :else
      (t2/select (keyword "models" (dc/pluralize (name attribute)))
                 (get instance (keyword (str (name attribute) "-id")))))))
