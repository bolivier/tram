(ns tram.vars
  "Namespace for dynamic vars.")

(def ^:dynamic *current-user*
  "The currently authenticated user.

  Automatically populated in views for a request."
  nil)
(def ^:dynamic *req*
  "The current request.

  Automatically populated in views for a request."
  nil)
(def ^:dynamic *res*
  "The current response.

  Automatically populated in views for a request."
  nil)
