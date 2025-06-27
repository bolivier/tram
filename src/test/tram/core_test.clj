(ns tram.core-test
  (:require [clojure.string :as str]
            [expectations.clojure.test :as e]
            [malli.core :as m]
            [malli.error :as me]
            [tram.core :as sut]))

(e/defexpect configuration-output
  (let [project-name "test-project"
        config       (sut/generate-config project-name)]
    (e/expect (sut/valid-config? config)
              true
              (me/humanize (m/validate sut/DatabaseConfigSchema config)))
    (e/expect #(str/ends-with? % "_production")
              (get-in config [:database/production :db :dbname]))
    (e/expect #(str/ends-with? % "_development")
              (get-in config [:database/development :db :dbname]))
    (e/expect #(str/ends-with? % "_test")
              (get-in config [:database/test :db :dbname])))
)
