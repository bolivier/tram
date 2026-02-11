(ns rapid-test.html.utils
  (:require [clojure.string :as str]
            [clojure.zip :as zip]
            [rapid-test.hiccup-zipper :refer [hiccup-zipper]]
            [tram.utils :refer [omit-by]]))

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
        tag-str    (name (first hiccup))
        marker?    #{\# \.}
        props      (loop [props (update base-props :class vector)
                          chars (drop-while (complement marker?) tag-str)]
                     (if (empty? chars)
                       props
                       (let [marker      (first chars)
                             [value rst] (split-with (complement marker?)
                                                     (rest chars))
                             value       (apply str
                                           value)]
                         (recur (case marker
                                  \# (update props
                                             :id
                                             #(or (:id %)
                                                  (keyword value)))
                                  \. (update props
                                             :class
                                             conj
                                             value))
                                rst))))]
    (omit-by #(or (nil? %) (= "" %))
             (update props :class #(str/join " " (remove nil? %))))))

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

(defn inside-sectioning-content?
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

(defn find-by-id
  "Traverse the hiccup tree to find the element with a matching :id attribute."
  [id root]
  (loop [hzip (hiccup-zipper root)]
    (if (zip/end? hzip)
      nil
      (let [node (zip/node hzip)]
        (if (and (not (string? node))
                 (let [node-id (get-attribute node :id)]
                   (and node-id (= (name id) (name node-id)))))
          node
          (recur (zip/next hzip)))))))

(defn text-match?
  "Compare text; supports both exact string and regex pattern."
  [pattern text]
  (if (instance? java.util.regex.Pattern pattern)
    (boolean (re-find pattern (or text "")))
    (= pattern text)))

(defn get-accessible-name [hiccup]
  (or (get-attribute hiccup :aria-label) (get-text hiccup)))

(def ^:private heading-tag->level
  {:h1 1
   :h2 2
   :h3 3
   :h4 4
   :h5 5
   :h6 6})

(defn get-heading-level [hiccup]
  (or (some-> (get-attribute hiccup :aria-level)
              str
              parse-long)
      (get heading-tag->level (get-base-tag hiccup))))

(defn matches-options? [hiccup opts]
  (let [{:keys [name level]} opts]
    (and (if name
           (text-match? name (get-accessible-name hiccup))
           true)
         (if level
           (= level (get-heading-level hiccup))
           true))))
