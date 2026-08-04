(ns rhizome.core
  (:require [rhizome.mount :as mount]
            [rhizome.triggers :as triggers]))

(defn init []
  (triggers/register! triggers/bind)
  (triggers/register! triggers/text)
  (triggers/register! triggers/click)
  (triggers/register! triggers/submit)
  (mount/mount!)
  (mount/observe!))
