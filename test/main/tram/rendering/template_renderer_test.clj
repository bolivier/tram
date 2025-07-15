(ns main.tram.rendering.template-renderer-test
  (:require [expectations.cloure.test :as e]
            [tram.rendering.template-renderer :as sut]))

(e/defexpect renderer
  (e/expect nil (sut/render {})))
