(ns tram.associations
  "Associations are managed through the *associations* dynamic var. The keys of
  the map are models that have some asociations defined on them.

  For now these take a couple forms:

  {:models/users {:has-many {:models/settings {:through nil}}}}

  is a association that says there is a settings table and a users table and
  they are connected through a join table using the default conventional name:
  setting_users. That is configurable with `:through`.

  The other type is

  {:models/accounts {:owns #{:models/users}}}

  This association means that there is a table users, which has a foreign key
  `:account-id` to the table accounts.

  If there are no associations, then
  `toucan2.core/model-for-automagic-hydration` should return a model keyword for
  the model to hydrate. This works in the 'has-a' case without configuration.

  If you have registered an association, then
  `toucan2.core/model-for-automagic-hydration` should return `nil` for that
  model/key combination. When that happens, the hydration mechanism should
  fallthrough to `toucan2.core/simple-hydrate`, which has been extended to do
  the has-many and belongs-to lookups."
  (:require [declensia.core :as dc]
            [methodical.core :as m]
            [toucan2.core :as t2]
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

(defn has-one! [owner belonger {:keys [as]}]
  (swap! *associations*
    (fn [associations] (assoc-in associations [owner :has-one as] belonger))))

(defn has-many!
  "Create a association where `owner` has many `belonger`s.

  This is the inverse of a `belongs-to!` relationship so that the keys can be
  filled into a set."
  ([owner belonger]
   (has-many! owner belonger :one-to-many))
  ([owner belonger type]
   (def from
     from)
   (swap! *associations* (fn [associations]
                           (assoc-in associations
                             [owner :has-many belonger]
                             {:model      (lang/modelize belonger
                                                         {:plural? false})
                              :join-table (if (= :many-to-many type)
                                            (lang/join-table owner
                                                             belonger)
                                            nil)})))))

(defn belongs-to!
  "Creates a belongs-to association between `owner` and `belonger`.

  `belonger` has a foreign key to `owner`.

  This implemented with the term \"owns\" instead of \"belongs-to\" because of
  the direction of lookup in the general case."
  ([belonger owner _opts]
   (belongs-to! belonger owner))
  ([belonger owner]
   (swap! *associations* (fn [associations]
                           (->
                             associations
                             (update-in [belonger :belongs-to] set)
                             (update-in [belonger :belongs-to] conj owner))))))

(defn belongs-to?
  "Checks if `belonger` belongs-to `owner`.

  Expects both args to be fully qualified keywords with the
  ns :models/<some-model>."
  [belonger owner]
  (contains? (get-in @*associations* [belonger :belongs-to]) owner))

(defn has-many?
  "Checks if `model` has-many `attibute-model`s.

  Expects both args to be fully qualified keywords with the
  ns :models/<some-model>."
  [model attribute]
  (def model
    model)
  (def attribute-model
    attribute)
  (some? (get-in @*associations* [model :has-many attribute])))

(defn has-explicit-association?
  "Checks for an explicitly defined association between `base`, a fully qualified
  model keyword, and `attribute` a non-namespaced keyword representing the
  attribute keyword.

  For belongs-to associations, `attribute` should be a singular keyword.
  For has-many associations, `attribute` should be a plural keyword."
  [base attribute]
  (def base
    base)
  (def attribute
    attribute)
  (has-many? base attribute))

(m/defmethod t2/model-for-automagic-hydration [:default :default]
  [model k]
  (def model
    model)
  (def k
    k)
  (let [has-association (has-explicit-association? model k)
        alias-model     (get-in @*associations* [model :has-one k])]
    (if has-association
      nil
      (or alias-model
          (lang/modelize k)))))


(m/defmethod t2/simple-hydrate [:default :default]
  [model k instance]
  (def model
    model)
  (def k
    k)
  (def instance
    instance)
  (let [join-table (get-in @*associations* [model :has-many k :join-table])]
    (cond
      (some? join-table)
      (let [fk-for-instance (lang/table-name->foreign-key-id model)
            fk-for-other    (lang/table-name->foreign-key-id k)
            join-clause     [join-table
                             [:=
                              (keyword (str (name k) ".id"))
                              (keyword (str (name join-table)
                                            "."
                                            (name fk-for-other)))]]
            where-clause    [:=
                             (keyword (str (name join-table)
                                           "."
                                           (name fk-for-instance)))
                             (:id instance)]]
        (assoc instance
          k
          (t2/select (keyword "models" (name k))
                     {:join  join-clause
                      :where where-clause})))

      (some? (get-in @*associations* [model :has-many k :model]))
      (assoc instance
        k
        (t2/select (lang/modelize k {:plural? false})
                   (or (get-in @*associations* [model :has-many k :from])
                       (keyword (lang/table-name->foreign-key-id (name model))))
                   (:id instance)))

      (contains? (get-in @*associations* [model :belongs-to]) (lang/modelize k))
      (assoc instance
        k
        (t2/select-one (lang/modelize k)
                       (get instance (lang/table-name->foreign-key-id k))))

      :else
      (t2/select (keyword "models" (dc/pluralize (name k)))
                 (get instance (keyword (str (name k) "-id")))))))
