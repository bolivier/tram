(ns rapid-test.html.label
  (:require [clojure.zip :as zip]
            [rapid-test.hiccup-zipper :refer [hiccup-zipper]]
            [rapid-test.html.utils :as util]))

(defn- labeled-by-for?
  "Check if a <label for=\"X\"> exists whose text matches, and the current node's id is X."
  [label-text hzip root]
  (let [node    (zip/node hzip)
        node-id (util/get-attribute node :id)]
    (when node-id
      (loop [lzip (hiccup-zipper root)]
        (if (zip/end? lzip)
          false
          (let [lnode (zip/node lzip)]
            (if (and (not (string? lnode))
                     (= :label (util/get-base-tag lnode))
                     (let [for-attr (util/get-attribute lnode :for)]
                       (and for-attr (= (name for-attr) (name node-id))))
                     (util/text-match? label-text (util/get-text lnode)))
              true
              (recur (zip/next lzip)))))))))

(defn- labeled-by-wrapping?
  "Check if the current node is inside a <label> ancestor whose text matches."
  [label-text hzip]
  (loop [loc (zip/up hzip)]
    (if (nil? loc)
      false
      (let [node (zip/node loc)]
        (if (and (vector? node)
                 (= :label (util/get-base-tag node))
                 (util/text-match? label-text (util/get-text node)))
          true
          (recur (zip/up loc)))))))

(defn- labeled-by-aria-label?
  "Check if the node's aria-label matches."
  [label-text hiccup]
  (let [aria-label (util/get-attribute hiccup :aria-label)]
    (and aria-label (util/text-match? label-text aria-label))))

(defn- labeled-by-aria-labelledby?
  "Check if the node has aria-labelledby, resolve the referenced element, get its text, and compare."
  [label-text hiccup root]
  (let [labelledby (util/get-attribute hiccup :aria-labelledby)]
    (when labelledby
      (let [referenced (util/find-by-id labelledby root)]
        (and referenced
             (util/text-match? label-text (util/get-text referenced)))))))

(defn- label-match?
  "Check if a node is labeled by the given text via any of the four mechanisms."
  [label-text hzip root]
  (let [node (zip/node hzip)]
    (or (labeled-by-for? label-text hzip root)
        (labeled-by-wrapping? label-text hzip)
        (labeled-by-aria-label? label-text node)
        (labeled-by-aria-labelledby? label-text node root))))

(defn get-by-label
  "Return the first element labeled by `label-text`, or nil."
  [label-text hiccup]
  (loop [hzip (hiccup-zipper hiccup)]
    (if (zip/end? hzip)
      nil
      (let [node (zip/node hzip)]
        (if (and (not (string? node))
                 (not= :label (util/get-base-tag node))
                 (label-match? label-text hzip hiccup))
          node
          (recur (zip/next hzip)))))))

(defn get-all-by-label
  "Return a vector of all elements labeled by `label-text`."
  [label-text hiccup]
  (loop [hzip    (hiccup-zipper hiccup)
         results []]
    (if (zip/end? hzip)
      results
      (let [node (zip/node hzip)]
        (if (and (not (string? node))
                 (not= :label (util/get-base-tag node))
                 (label-match? label-text hzip hiccup))
          (recur (zip/next hzip)
                 (conj results node))
          (recur (zip/next hzip)
                 results))))))
