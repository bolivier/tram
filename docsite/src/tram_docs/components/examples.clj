(ns tram-docs.components.examples)

(defn demo-card
  "Chrome that marks the running demo off from the prose around it.

  Goes inside an example's morph target, so a swap replaces the demo and
  leaves the card alone.

  Takes an optional attribute map ahead of its children, so an example can hang
  a directive on the card itself."
  [& args]
  (let [[attrs children] (if (map? (first args))
                           [(first args) (rest args)]
                           [{} args])]
    [:div.demo-card
     attrs
     [:p.demo-card-label "Demo"]
     [:div.demo-card-body children]]))
