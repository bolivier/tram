(ns rapid-test.html
  "HTML testing utils inspired by Testing Library (https://testing-library.com/)"
  (:require [clojure.string :as str]
            [clojure.zip :as zip]
            [methodical.core :as m]
            [rapid-test.hiccup-zipper :refer [hiccup-zipper]]))

(defn has-props?
  "Return whether the hiccup has an explicit props map."
  [hiccup]
  (map? (second hiccup)))

(defn get-props
  "Get the props for a hiccup element"
  [hiccup]
  (let [base-props (if (has-props? hiccup)
                     (second hiccup)
                     {})
        id         (atom nil)
        class-sb   (StringBuilder.)
        _ (.append class-sb (or (:class base-props) ""))
        tag-str    (name (first hiccup))

        props      (transient base-props)]



    (loop [tag-props-seq (rest (map #(apply str %)
                                    (partition-by #{\. \#} tag-str)))]
      (when-not (empty? tag-props-seq)
        (let [[marker-char value & rst] tag-props-seq]
          (case marker-char
            "#" (do (reset! id
                            (keyword value))
                    (recur rst))
            "." (do (.append class-sb
                             (format " %s"
                                     value))
                    (recur rst))))))
    (let [class (str/trim (.toString class-sb))]
      (when-not (empty? class)
        (assoc! props
                :class
                class)))
    (when (and @id
               (not (:id base-props)))
      (assoc! props
              :id
              @id))
    (persistent! props)))

(defn get-attribute
  "Get `attribute` from `hiccup`. Works when attribute is from a compound tag."
  [hiccup attr]
  (let [props (get-props hiccup)]
    (get props attr)))

(defn get-base-tag
  "Convert a compound tag into a simple tag.

  :div#my-id.my-class => :div"
  [hiccup]
  (let [tag (first hiccup)]
    (->> tag
         name
         (take-while #(and (not= \# %) (not= \. %)))
         (apply str)
         keyword)))

(m/defmulti role-match?
  "Return a node from a hiccup tree that has the html role `role`.

   Note: Takes a hiccup zipper and not plain hiccup"
  (fn [role _] role))

(m/defmethod role-match? :list
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (contains? #{:menu :ol :ul} (get-base-tag hiccup))
        (= :list (get-attribute hiccup :role)))))

(m/defmethod role-match? :listitem
  [_ hzip]
  (let [tag (get-base-tag (zip/node hzip))]
    (if (= :li tag)
      (let [parent-el (when-let [parent (zip/up hzip)]
                        (get-base-tag (zip/node parent)))]
        (or (= parent-el :ol)
            (= parent-el :ul)
            (= parent-el :menu)))
      false)))

(m/defmethod role-match? :default
  [role hzip]
  (let [hiccup (zip/node hzip)]
    (throw (ex-info (format "`role-match?` not implemented for role `%s`" role)
                    {:role   role
                     :hiccup hiccup}))))

(defn get-by-role [role hiccup]
  (loop [hzip (hiccup-zipper hiccup)]
    (if (zip/end? hzip)
      nil
      (if (and (not (string? (zip/node hzip)))
               (role-match? role
                            hzip))
        (zip/node hzip)
        (recur (zip/next hzip))))))

(defn get-text
  "Get the (nested) text of a hiccup node."
  ([hiccup]
   (.toString (get-text (StringBuilder.) (hiccup-zipper hiccup))))
  ([sb hzip]
   (if (zip/end? hzip)
     sb
     (let [node (zip/node hzip)]
       (when (string? node)
         (.append sb
                  node))
       (recur sb
              (zip/next hzip))))))

(comment
  (def hiccup
    [:input.inpt#my-id])
  (def attr
    :id)
  (def role
    :list)
  (def hiccup
    [:p
     "The "
     [:a {:href "#"}
      [:code "Rapid Test"]]
     " library helps you test UI components."])
  (def hiccup
    [:div [:span "errors: "] [:ul#error-list [:li "Something went wrong!"]]]))
