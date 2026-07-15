(ns sample-app.core
  (:gen-class)
  (:require [integrant.core :as ig]
            [sample-app.config :as c]
            [sample-app.db]
            ;; Registers model hooks; a :models/... keyword loads no
            ;; namespace.
            [sample-app.models.session]
            [sample-app.models.user]
            [sample-app.server]
            [tram.logging :as log]))

(defn start-app []
  (->> c/system
       (ig/prep)
       (ig/init))
  (log/event! "System started"))

(defn -main []
  (start-app))
