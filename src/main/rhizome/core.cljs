(ns rhizome.core
  (:require [rhizome.mount :as mount]
            [rhizome.triggers :as triggers]))

(defn init []
  (triggers/register! triggers/bind)
  (triggers/register! triggers/text)
  (triggers/register! triggers/click)
  (mount/mount!)
  (mount/observe!))
