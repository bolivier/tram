(ns sample-app.config
  (:require [buddy.core.codecs :as codecs]
            [buddy.core.nonce :as nonce]
            [integrant.core :as ig]))

(def csrf-secret
  "Secret key for signing CSRF tokens. Uses CSRF_SECRET env var if available,
  otherwise generates a random key (tokens won't survive restarts)."
  (or (System/getenv "CSRF_SECRET")
      (codecs/bytes->hex (nonce/random-bytes 32))))

(defonce system
  {::server {:port 1337
             :app  (ig/ref ::app)}
   ::app    {:router (ig/ref ::router)}
   ::router {:routes      (ig/ref ::routes)
             :csrf-secret csrf-secret}
   ::routes {}})
