(ns sample-app.config
  (:require [integrant.core :as ig]))

(defonce system
  {::server {:port 1337
             :app  (ig/ref ::app)}
   ::app    {:router (ig/ref ::router)}
   ::router {:routes (ig/ref ::routes)}
   ::routes {}})
