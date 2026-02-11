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

(def ^:private simple-role->tags
  {:heading       #{:h1 :h2 :h3 :h4 :h5 :h6}
   :img           #{:img}
   :table         #{:table}
   :row           #{:tr}
   :cell          #{:td}
   :columnheader  #{:th}
   :separator     #{:hr}
   :article       #{:article}
   :figure        #{:figure}
   :navigation    #{:nav}
   :main          #{:main}
   :complementary #{:aside}
   :form          #{:form}
   :region        #{:section}
   :dialog        #{:dialog}
   :meter         #{:meter}
   :progressbar   #{:progress}
   :option        #{:option}
   :search        #{:search}})

(defn- input-type?
  "True if hiccup is an <input> with type in type-set. nil in type-set matches inputs with no type."
  [hiccup type-set]
  (and (= :input (get-base-tag hiccup))
       (let [raw-type   (get-attribute hiccup :type)
             input-type (when raw-type
                          (name raw-type))]
         (contains? type-set input-type))))

(defn- explicit-role?
  "True if hiccup has an explicit role attribute matching the given role keyword."
  [hiccup role]
  (let [r (get-attribute hiccup :role)]
    (and r (= (name role) (name r)))))

(defn- inside-sectioning-content?
  "True if the zipper location is inside an article, aside, main, nav, or section element."
  [hzip]
  (let [sectioning-tags #{:article :aside :main :nav :section}]
    (loop [loc (zip/up hzip)]
      (if (nil? loc)
        false
        (let [node (zip/node loc)]
          (if (and (vector? node)
                   (contains? sectioning-tags
                              (get-base-tag node)))
            true
            (recur (zip/up loc))))))))

(m/defmethod role-match? :button
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (= :button (get-base-tag hiccup))
        (input-type? hiccup #{"button" "submit" "reset"})
        (explicit-role? hiccup :button))))

(m/defmethod role-match? :textbox
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (= :textarea (get-base-tag hiccup))
        (input-type? hiccup #{"text" nil})
        (explicit-role? hiccup :textbox))))

(m/defmethod role-match? :checkbox
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (input-type? hiccup #{"checkbox"}) (explicit-role? hiccup :checkbox))))

(m/defmethod role-match? :radio
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (input-type? hiccup #{"radio"}) (explicit-role? hiccup :radio))))

(m/defmethod role-match? :searchbox
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (input-type? hiccup #{"search"}) (explicit-role? hiccup :searchbox))))

(m/defmethod role-match? :slider
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (input-type? hiccup #{"range"}) (explicit-role? hiccup :slider))))

(m/defmethod role-match? :spinbutton
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (input-type? hiccup #{"number"}) (explicit-role? hiccup :spinbutton))))

(m/defmethod role-match? :link
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (and (= :a (get-base-tag hiccup)) (some? (get-attribute hiccup :href)))
        (explicit-role? hiccup :link))))

(m/defmethod role-match? :banner
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (and (= :header (get-base-tag hiccup))
             (not (inside-sectioning-content? hzip)))
        (explicit-role? hiccup :banner))))

(m/defmethod role-match? :contentinfo
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (and (= :footer (get-base-tag hiccup))
             (not (inside-sectioning-content? hzip)))
        (explicit-role? hiccup :contentinfo))))

(m/defmethod role-match? :rowheader
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (and (= :th (get-base-tag hiccup))
             (= "row"
                (some-> (get-attribute hiccup :scope)
                        name)))
        (explicit-role? hiccup :rowheader))))

(m/defmethod role-match? :combobox
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (and (= :select (get-base-tag hiccup))
             (not (get-attribute hiccup :multiple))
             (let [size (get-attribute hiccup :size)]
               (or (nil? size) (<= (long size) 1))))
        (explicit-role? hiccup :combobox))))

(m/defmethod role-match? :default
  [role hzip]
  (let [hiccup (zip/node hzip)
        tag    (get-base-tag hiccup)]
    (or (contains? (get simple-role->tags role) tag)
        (explicit-role? hiccup role))))

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

(defn- get-accessible-name [hiccup]
  (or (get-attribute hiccup :aria-label) (get-text hiccup)))

(def ^:private heading-tag->level
  {:h1 1
   :h2 2
   :h3 3
   :h4 4
   :h5 5
   :h6 6})

(defn- get-heading-level [hiccup]
  (or (some-> (get-attribute hiccup :aria-level)
              str
              parse-long)
      (get heading-tag->level (get-base-tag hiccup))))

(defn- matches-options? [hiccup opts]
  (let [{:keys [name level]} opts]
    (and (if name
           (let [accessible-name (get-accessible-name hiccup)]
             (if (instance? java.util.regex.Pattern
                            name)
               (boolean (re-find name
                                 (or accessible-name
                                     "")))
               (= name accessible-name)))
           true)
         (if level
           (= level (get-heading-level hiccup))
           true))))

(defn get-by-role
  ([role hiccup]
   (get-by-role role hiccup {}))
  ([role hiccup opts]
   (loop [hzip (hiccup-zipper hiccup)]
     (if (zip/end? hzip)
       nil
       (let [node (zip/node hzip)]
         (if (and (not (string? node))
                  (role-match? role
                               hzip)
                  (matches-options? node
                                    opts))
           node
           (recur (zip/next hzip))))))))

(defn get-all-by-role
  ([role hiccup]
   (get-all-by-role role hiccup {}))
  ([role hiccup opts]
   (loop [hzip    (hiccup-zipper hiccup)
          results []]
     (if (zip/end? hzip)
       results
       (let [node (zip/node hzip)]
         (if (and (not (string? node))
                  (role-match? role
                               hzip)
                  (matches-options? node
                                    opts))
           (recur (zip/next hzip)
                  (conj results
                        node))
           (recur (zip/next hzip)
                  results)))))))

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
