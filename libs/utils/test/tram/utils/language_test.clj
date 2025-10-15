(ns tram.utils.language-test
  (:require [clojure.test :refer [deftest is]]
            [tram.utils.language :as sut]))

(deftest join-table-name
  (is (= :model-users (sut/get-join-table :models/users :models/models)))
  (is (= :car-wheels (sut/get-join-table :models/cars :models/wheels))))
