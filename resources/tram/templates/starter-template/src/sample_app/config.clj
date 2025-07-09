(ns sample-app.config
  (:require [integrant.core :as ig]))

(defonce system
  {::server {:port 1337
             :app  (ig/ref ::app)}
   ::app    {:routes (ig/ref ::routes)}
   ::routes {}})
