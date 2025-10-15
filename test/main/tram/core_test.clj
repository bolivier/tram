(ns tram.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [malli.core :as m]
            [malli.error :as me]
            [tram.core :as sut]))

(deftest configuration-output
  (let [project-name "test-project"
        config       (sut/generate-config project-name)]
    (is (= (sut/valid-config? config) true)
        (me/humanize (m/validate sut/DatabaseConfigSchema config)))
    (is (match? #(str/ends-with? % "_production")
                (get-in config [:database/production :db :dbname])))
    (is (match? #(str/ends-with? % "_development")
                (get-in config [:database/development :db :dbname])))
    (is (match? #(str/ends-with? % "_test")
                (get-in config [:database/test :db :dbname])))))
