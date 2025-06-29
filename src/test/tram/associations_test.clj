(ns tram.associations-test
  (:require [expectations.clojure.test :as e]
            [tram.associations :as sut]
            [tram.core]))

(binding [sut/*relationships* (atom {})]
  (e/defexpect relationship-methods

    (sut/has-many! :model/users :model/settings :through :users-settings)

    (e/expect {:has-many {:model/settings {:through :users-settings}}}
              (:model/users @sut/*relationships*))

    (sut/belongs-to! :model/projects :model/attributes)

    (e/expect {:belongs-to #{:model/attributes}}
              (:model/projects @sut/*relationships*))))
