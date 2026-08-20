(ns rhizome.core
  (:require [rhizome.behaviors :as behaviors]
            [rhizome.mount :as mount]))

(defn init []
  (behaviors/register! behaviors/bind)
  (behaviors/register! behaviors/text)
  (behaviors/register! behaviors/show)
  (behaviors/register! behaviors/debug)
  (behaviors/register! behaviors/click)
  (behaviors/register! behaviors/submit)
  (behaviors/register! behaviors/input)
  (behaviors/register! behaviors/load)
  (behaviors/register! behaviors/drop)
  (behaviors/register! behaviors/dragover)
  (mount/mount!)
  (mount/observe!))
