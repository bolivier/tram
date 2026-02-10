(ns rapid-test.hiccup-zipper
  (:require [clojure.zip :as zip]))

(defn element?
  "True when node looks like a hiccup element vector."
  [node]
  (and (vector? node)
       (let [t (first node)]
         (or (keyword? t) (symbol? t) (string? t)))))

(defn props-map? [x]
  (map? x))

(defn children
  "Returns the child nodes of a hiccup element."
  [node]
  (let [[_ maybe-props & more] node]
    (loop [children       []
           maybe-children (list maybe-props more)]
      (if (empty? maybe-children)
        (seq children)
        (let [c (first maybe-children)]
          (cond
            (or (nil? c)
                (map? c))
            (recur children
                   (rest maybe-children))

            (seq? c)
            (recur children
                   (concat c
                           (rest maybe-children)))

            :else
            (recur (conj children
                         c)
                   (rest maybe-children))))))))

(defn make-node
  "Rebuilds a hiccup node from the original node + new children."
  [node new-children]
  (let [[tag maybe-props & _more] node]
    (if (props-map? maybe-props)
      (into [tag maybe-props]
            new-children)
      (into [tag]
            new-children))))

(defn hiccup-zipper
  "Creates a zipper over a hiccup tree.
  Only element vectors are treated as branch nodes."
  [root]
  (zip/zipper element? children make-node root))
