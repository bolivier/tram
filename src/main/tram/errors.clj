(ns tram.errors)

(defn not-yet-implemented
  ([]
   (not-yet-implemented {}))
  ([data]
   (ex-info "Not yet implemened" (merge {:type ::validation-error} data))))
