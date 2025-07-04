(ns sample-app.core
  (:gen-class)
  (:require [integrant.core :as ig]
            [sample-app.config :as c]
            [sample-app.db]
            [sample-app.server]
            [taoensso.telemere :as t]))

(defn start-app []
  (t/log! "Starting system")
  (->> c/system
       (ig/prep)
       (ig/init))
  (t/log! "Started system"))

(defn -main []
  (start-app))
