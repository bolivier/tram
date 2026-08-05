(ns tram-docs.components.examples)

(defn demo-card
  "Chrome that marks the running demo off from the prose around it.

  Goes inside an example's morph target, so a swap replaces the demo and
  leaves the card alone."
  [& children]
  [:div.demo-card
   [:p.demo-card-label "Demo"]
   [:div.demo-card-body children]])
