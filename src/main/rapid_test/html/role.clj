(ns rapid-test.html.role
  (:require [clojure.zip :as zip]
            [methodical.core :as m]
            [rapid-test.hiccup-zipper :refer [hiccup-zipper]]
            [rapid-test.html.utils :as util]))

(m/defmulti role-match?
  "Return a node from a hiccup tree that has the html role `role`.

   Note: Takes a hiccup zipper and not plain hiccup"
  (fn [role _] role))

(m/defmethod role-match? :list
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (contains? #{:menu :ol :ul} (util/get-base-tag hiccup))
        (= :list (util/get-attribute hiccup :role)))))

(m/defmethod role-match? :listitem
  [_ hzip]
  (let [tag (util/get-base-tag (zip/node hzip))]
    (if (= :li tag)
      (let [parent-el (when-let [parent (zip/up hzip)]
                        (util/get-base-tag (zip/node parent)))]
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
  (and (= :input (util/get-base-tag hiccup))
       (let [raw-type   (util/get-attribute hiccup :type)
             input-type (when raw-type
                          (name raw-type))]
         (contains? type-set input-type))))

(defn- explicit-role?
  "True if hiccup has an explicit role attribute matching the given role keyword."
  [hiccup role]
  (let [r (util/get-attribute hiccup :role)]
    (and r (= (name role) (name r)))))

(m/defmethod role-match? :button
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (= :button (util/get-base-tag hiccup))
        (input-type? hiccup #{"button" "submit" "reset"})
        (explicit-role? hiccup :button))))

(m/defmethod role-match? :textbox
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (= :textarea (util/get-base-tag hiccup))
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
    (or (and (= :a (util/get-base-tag hiccup))
             (some? (util/get-attribute hiccup :href)))
        (explicit-role? hiccup :link))))

(m/defmethod role-match? :banner
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (and (= :header (util/get-base-tag hiccup))
             (not (util/inside-sectioning-content? hzip)))
        (explicit-role? hiccup :banner))))

(m/defmethod role-match? :contentinfo
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (and (= :footer (util/get-base-tag hiccup))
             (not (util/inside-sectioning-content? hzip)))
        (explicit-role? hiccup :contentinfo))))

(m/defmethod role-match? :rowheader
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (and (= :th (util/get-base-tag hiccup))
             (= "row"
                (some-> (util/get-attribute hiccup :scope)
                        name)))
        (explicit-role? hiccup :rowheader))))

(m/defmethod role-match? :combobox
  [_ hzip]
  (let [hiccup (zip/node hzip)]
    (or (and (= :select (util/get-base-tag hiccup))
             (not (util/get-attribute hiccup :multiple))
             (let [size (util/get-attribute hiccup :size)]
               (or (nil? size) (<= (long (cond-> size (string? size) parse-long)) 1))))
        (explicit-role? hiccup :combobox))))

(m/defmethod role-match? :default
  [role hzip]
  (let [hiccup (zip/node hzip)
        tag    (util/get-base-tag hiccup)]
    (or (contains? (get simple-role->tags role) tag)
        (explicit-role? hiccup role))))

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
                  (util/matches-options? node
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
                  (util/matches-options? node
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
