(ns tram.generators.domain
  (:require
   [honey.sql :as sql]))

(defn table [name]
  (sql/)
  (sql/format [:CREATE :TABLE name])
  )

(def timestamps
  [[:created_at :timestamptz :NOT :NULL :DEFAULT :fn/NOW]
   [:updated_at :timestamptz :NOT :NULL :DEFAULT :fn/NOW]])

(defn replace-with-more-accurate-type [line opts]
  (if (= :insensitive (:case opts))
    line line))

(defn add-unique [line opts]
  line)
(defn set-nullable [line opts])

(defn generate-migration-code [table-kw table-def]
  (let [table (table table-kw)
        defs (mapv
              (fn [[name type opts]]
                [name type])
              (concat
               [[:id :serial :PRIMARY :KEY]]
               table-def))]
    [(into  table
            [(into
              [[:id :serial :PRIMARY :KEY]]
              (into defs
                    timestamps))])]))

;; I want to take a structure like this and convert it into a handful of pieces
;; of Clojure code.
;;
;;I want to create the authentication dir.
;; - models/<model>.clj
;;   - Originally I didn't want to do much with these, but I think some of
;;     the default find/update/etc functions are going to be useful here.
;;     I could possibly get away with just using some Toucan stuff for that, though.
;;     I may need some toucan default error handling.
;;
;; - store.clj
;;   - creates no methods by default

;; This key should trigger creation of the root level authentication dir
{:domain :authentication

 ;; This key should trigger creation of the routes listed, and have them inserted into the main
 ;; router.  The functions should get created with the
 ;; name `(str (name (:name route)) (blank-for-get-or-verb) "-handler")`
 ;; These can just return a 200
 :routes [["/sign-up" {:name    :route/sign-up
                       :methods #{:get :post}}]
          ["/sign-in" {:name    :route/sign-in
                       :methods #{:get :post}}]
          ["/log-out" {:name    :route/log-out
                       :methods #{:get}}]]

 ;; This key should trigger creating these in a single migration with inferred
 ;; join tables (review rails syntax here)
 :models {:accounts []

          ;; These keys should also generate some kind of
          ;; hydrateable models.
          :teams    [:belongs-to :accounts]
          :users    [[:name :text {:required true}]
                     [:email :text {:required true
                                    :unique   true}]
                     [:password :text {:required true
                                       ;; automatically removed from model on select, andf
                                       ;; has a special function to get it in isolation from the id
                                       :secret   true}]
                     [:has-many :teams]]
          :sessions [[:belongs-to :users]

                     [:expires-at :timestamptz {:required true}]]}}
