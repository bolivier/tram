(ns tram.http.routing
  (:require [reitit.core :as r]))

(defn make-path
  "Convert a keyword name of a route into the route name.

  `router` - your tram router
  `route-name` - keyword of the route to convert.  This is usually
                 something like `:route/dashboard`.
  `route-params` - optional params to replace in the url.
                   This is for a route like `/users/:user-id` and you'd pass
                   `{:user-id 1}`"
  ([router route-name]
   (make-path router route-name {}))
  ([router route-name route-params]
   (:path (r/match-by-name router route-name route-params))))
