(ns rhizome.core
  (:require [rhizome.mount :as mount]
            [rhizome.triggers :as triggers]))

(defn init []
  (triggers/register! triggers/bind)
  (triggers/register! triggers/text)
  (triggers/register! triggers/click)
  (triggers/register! triggers/submit)
  (triggers/register! triggers/input)
  (triggers/register! triggers/load)
  (mount/mount!)
  (mount/observe!))
