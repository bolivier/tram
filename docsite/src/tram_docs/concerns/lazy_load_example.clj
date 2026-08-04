(ns tram-docs.concerns.lazy-load-example)

(def retrieval-delay-ms
  "Long enough that the placeholder is visible. The whole point of the example
  is the moment before the content lands."
  600)

(def dossier
  [{:label "Held by"
    :value "Ugarte"}
   {:label "Signed by"
    :value "General Weygand"}
   {:label "Bearers"
    :value "Two, unnamed"}
   {:label "Last seen"
    :value "Sam's piano, Café Américain"}])

(defn fetch-dossier []
  (Thread/sleep retrieval-delay-ms)
  dossier)
