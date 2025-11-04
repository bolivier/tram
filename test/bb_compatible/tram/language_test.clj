(ns tram.language-test
  (:require [clojure.test :refer [deftest is]]
            test-app.handlers.admin.users-handlers
            test-app.handlers.authentication-handlers
            test-app.views.admin.users-views
            [tram.language :as sut]))

(def views-ns
  (the-ns 'test-app.views.authentication-views))
(def handlers-ns
  (the-ns 'test-app.handlers.authentication-handlers))

(deftest convert-handler-ns-test
  (is (= (str views-ns) (sut/convert-ns handlers-ns :view)))
  (is (= (str handlers-ns) (sut/convert-ns handlers-ns :handler))))

(deftest convert-view-ns-test
  (is (= (str views-ns) (sut/convert-ns views-ns :view)))
  (is (= (str handlers-ns) (sut/convert-ns views-ns :handler))))

(deftest convert-nested-ns-test
  (let [views-ns    (the-ns 'test-app.views.admin.users-views)
        handlers-ns (the-ns 'test-app.handlers.admin.users-handlers)]
    (is (= (str views-ns) (sut/convert-ns views-ns :view)))
    (is (= (str handlers-ns) (sut/convert-ns views-ns :handler)))))

(deftest convert-nested-ns-edge-case-test
  (require '[test-app.views.admin.animal-handlers-views]
           '[test-app.handlers.admin.animal-handlers-handlers])
  (let [views-ns    (the-ns 'test-app.views.admin.animal-handlers-views)
        handlers-ns (the-ns 'test-app.handlers.admin.animal-handlers-handlers)]
    (is (= (str views-ns) (sut/convert-ns views-ns :view)))
    (is (= (str handlers-ns) (sut/convert-ns views-ns :handler)))))

(deftest modelize-singular
  (let [tests [[:models/users :users]
               [:models/users :models/users]
               [:models/settings :settings]]]
    (doseq [[expected input] tests]
      (is (= expected (sut/modelize input {:plural? false}))))))

(deftest modelize-plural
  (let [tests [[:models/users :user]
               [:models/users :models/user]
               [:models/settings :setting]]]
    (doseq [[expected input] tests]
      (is (= expected (sut/modelize input))))))

(deftest ns->filename-test
  (is (= "some/nested_route/my_file.clj"
         (sut/ns->filename "some.nested-route.my-file"))))

(deftest ns->filename-multiarity-test
  (is (= "some/nested_route/my_file.clj"
         (sut/ns->filename "some" "nested-route" "my-file"))))

(deftest filename->ns-multiarity-test
  (is (= "some.nested-route.my-file"
         (sut/filename->ns "some" "nested_route" "my_file.clj"))))
