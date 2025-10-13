(ns tram-cli.entry-test
  (:require [babashka.fs :as fs]
            [clojure.java.io :refer [make-parents]]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing] :as t]
            [rapid-test.core :as rt]
            [tram-cli.entry :as sut]
            [tram-cli.generate]
            [tram-cli.generator.new :refer [render-new-project-template]]))

(defn match-snapshot
  "Check if the contents from sut are a match to the snapshot matching `snapshot-name`.

  `snapshot-name` should be a fully qualified keyword that will correspond to a
  file at test/snapshots."
  [actual snapshot-name]
  (let [snapshot-filename (str "test/snapshots/"
                               (-> snapshot-name
                                   str
                                   (str/replace #"\." "/")
                                   (str/replace #"^:" "")
                                   (str ".snapshot")))
        snapshot-contents (try
                            (slurp snapshot-filename)
                            (catch Exception e
                              (println "Could not find snapshot"
                                       snapshot-name
                                       "so it was created.")
                              (make-parents snapshot-filename)
                              (spit snapshot-filename actual)
                              actual))]
    (t/is (= snapshot-contents actual))))

(deftest generate-new-project
  (rt/with-stub [calls render-new-project-template]
    (sut/-main "new" "my-project")
    (is (match? {:args ["my-project"]} (first @calls)))))

(deftest generate-cmd
  (rt/with-stub [calls tram-cli.generate/do-generate]
    (sut/-main "generate" "migration")
    (sut/-main "g" "migration-shorthand")
    (is (match? [["migration"]] (:args (first @calls))))
    (is (match? [["migration-shorthand"]] (:args (second @calls))))))

(deftest testing-cmd
  (testing "kaocha version"
    (rt/with-stub [_ println
                   calls babashka.process/shell]
      (sut/-main "test")
      (sut/-main "test" "--watch")
      (is (= [["bin/kaocha"]] (:args (first @calls))))
      (is (= [["bin/kaocha --watch"]] (:args (second @calls))))))
  (testing "plain clj invocation"
    (rt/with-stub [_ println
                   _ {:fn      babashka.fs/exists?
                      :returns false}
                   calls babashka.process/shell]
      (sut/-main "test")
      (sut/-main "test" "--watch")
      (is (= [["clojure -X:test"]] (:args (first @calls))))
      (is (= [["clojure -X:test:watch"]] (:args (second @calls)))))))

(deftest testing-html-convert
  (let [sample-html
        "<div class=\"my-class\" id=\"foobar\"><span>my-content</span></div>"

        sample-hiccup [:div {:class "my-class"
                             :id    "foobar"}
                       [:span {}
                        "my-content"]]]
    (rt/with-stub [_ {:fn      babashka.process/shell
                      :returns {:out sample-html}}
                   calls prn]
      (sut/-main "hiccup")
      (is (match? sample-hiccup (first (:args (first @calls))))))))

(comment
  (deftest help-menu
    (match-snapshot (with-out-str (sut/-main "-h")) ::help-menu)))
