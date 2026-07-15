(ns sample-app.handlers.welcome-handlers
  (:require [tram.core :refer [defroutes]]))

(defroutes routes
  [["/"
    {:name :route/root
     :get  :view/welcome}]])
