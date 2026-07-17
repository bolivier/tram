(ns tram-docs.core
  (:gen-class)
  (:require [integrant.core :as ig]
            [tram-docs.config :as c]
            [tram-docs.db]
            [tram-docs.server]
            [tram.logging :as log]))

(defn start-app []
  (->> c/system
       (ig/prep)
       (ig/init))
  (log/event! "System started"))

(defn -main []
  (start-app))
