(ns sample-app.http.utils
  (:require [tram.http.route-helpers :refer [make-route]]))

(defn render-page [page-fn]
  (constantly {:status 200
               :body   (page-fn)}))



(defn redirect-to-home-handler [req]
  {:status  301
   :headers {"location" (make-route :route/sign-in)}})
