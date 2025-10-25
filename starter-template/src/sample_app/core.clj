(ns sample-app.core
  (:gen-class)
  (:require [integrant.core :as ig]
            [sample-app.config :as c]
            [sample-app.db]
            [sample-app.server]
            [tram.logging :as log]))

(defn start-app []
  (->> c/system
       (ig/prep)
       (ig/init))
  (log/event! "System started"))

(defn -main []
  (start-app))
