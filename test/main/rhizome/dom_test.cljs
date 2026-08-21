(ns rhizome.dom-test
  (:require [cljs.test :refer [deftest is]]
            [rhizome.dom :as sut]
            [rhizome.test-utils :refer [attach-files! fake-file]])
  (:require-macros [rhizome.macros :refer [with-html]]))

(deftest form->map-reads-named-controls-test
  (with-html [form
              [:form
               [:input {:name  "applicant"
                        :value "Ilsa Lund"}]
               [:input {:name  "destination"
                        :value "Lisbon"}]]]
             (is (= {:applicant   "Ilsa Lund"
                     :destination "Lisbon"}
                    (sut/form->map form)))))

(deftest form->map-skips-unnamed-controls-test
  (with-html [form
              [:form
               [:input {:value "no name, no body"}]
               [:input {:name  "applicant"
                        :value "Ilsa Lund"}]]]
             (is (= {:applicant "Ilsa Lund"} (sut/form->map form)))))

(deftest form->map-skips-unchecked-boxes-test
  (with-html [form
              [:form
               [:input {:name  "papers"
                        :type  "checkbox"
                        :value "forged"}]
               [:input {:checked true
                        :name    "visa"
                        :type    "checkbox"
                        :value   "granted"}]]]
             (is (= {:visa "granted"} (sut/form->map form)))))

(deftest form->map-skips-unselected-radios-test
  (with-html [form
              [:form
               [:input {:name  "flight"
                        :type  "radio"
                        :value "Berlin"}]
               [:input {:checked true
                        :name    "flight"
                        :type    "radio"
                        :value   "Lisbon"}]]]
             (is (= {:flight "Lisbon"} (sut/form->map form)))))

(deftest form->map-collects-repeated-names-test
  (with-html [form
              [:form
               [:input {:checked true
                        :name    "crew"
                        :type    "checkbox"
                        :value   "Rick"}]
               [:input {:checked true
                        :name    "crew"
                        :type    "checkbox"
                        :value   "Louis"}]
               [:input {:checked true
                        :name    "crew"
                        :type    "checkbox"
                        :value   "Sam"}]]]
             (is (= {:crew ["Rick" "Louis" "Sam"]} (sut/form->map form)))))

(deftest form->map-skips-disabled-controls-test
  (with-html [form
              [:form
               [:input {:disabled true
                        :name     "applicant"
                        :value    "Ugarte"}]
               [:input {:name  "destination"
                        :value "Lisbon"}]]]
             (is (= {:destination "Lisbon"} (sut/form->map form)))))

(deftest form->map-skips-buttons-test
  (with-html [form
              [:form
               [:input {:name  "applicant"
                        :value "Ilsa Lund"}]
               [:button {:name  "action"
                         :type  "submit"
                         :value "apply"}
                "Apply"]]]
             (is (= {:applicant "Ilsa Lund"} (sut/form->map form)))))

(deftest form->map-reads-a-multi-select-test
  (with-html [form
              [:form
               [:select {:multiple true
                         :name     "cargo"}
                [:option {:selected true
                          :value    "letters"}
                 "letters"]
                [:option {:value "gin"}
                 "gin"]
                [:option {:selected true
                          :value    "piano"}
                 "piano"]]]]
             (is (= {:cargo ["letters" "piano"]} (sut/form->map form)))))

(deftest form->map-includes-controls-tied-by-form-attribute-test
  (with-html [root
              [:div
               [:form#transit]
               [:input {:form  "transit"
                        :name  "applicant"
                        :value "Victor Laszlo"}]]]
             (is (= {:applicant "Victor Laszlo"}
                    (sut/form->map (.querySelector root "form"))))))

(deftest form->files-reads-a-selected-file-test
  (with-html
    [form
     [:form
      [:input {:name  "applicant"
               :value "Ilsa Lund"}]
      [:input {:name "papers"
               :type "file"}]]]
    (attach-files! (.querySelector form "[type=file]") [(fake-file "visa.txt")])
    (is (= ["visa.txt"] (mapv #(.-name %) (:papers (sut/form->files form)))))))

(deftest form->files-skips-an-empty-file-input-test
  (with-html [form
              [:form
               [:input {:name "papers"
                        :type "file"}]]]
             (is (= {} (sut/form->files form)))))

(deftest form->files-skips-an-unnamed-file-input-test
  (with-html [form
              [:form
               [:input {:type "file"}]]]
             (attach-files! (.querySelector form "[type=file]")
                            [(fake-file "visa.txt")])
             (is (= {} (sut/form->files form)))))

(deftest form->files-skips-a-disabled-file-input-test
  (with-html [form
              [:form
               [:input {:disabled true
                        :name     "papers"
                        :type     "file"}]]]
             (attach-files! (.querySelector form "[type=file]")
                            [(fake-file "visa.txt")])
             (is (= {} (sut/form->files form)))))

(deftest form->files-reads-a-multiple-file-input-test
  (with-html [form
              [:form
               [:input {:multiple true
                        :name     "papers"
                        :type     "file"}]]]
             (attach-files! (.querySelector form "[type=file]")
                            [(fake-file "visa.txt") (fake-file "exit.txt")])
             (is (= ["visa.txt" "exit.txt"]
                    (mapv #(.-name %) (:papers (sut/form->files form)))))))

(deftest bound-value-reads-a-file-inputs-filename-test
  (with-html [form
              [:form
               [:input {:name "papers"
                        :type "file"}]]]
             (let [input (.querySelector form "[type=file]")]
               (is (nil? (sut/bound-value input)))
               (attach-files! input [(fake-file "visa.txt")])
               (is (= "visa.txt" (sut/bound-value input))))))

(deftest bound-value-reads-a-multiple-file-input-as-a-vector-test
  (with-html [form
              [:form
               [:input {:multiple true
                        :name     "papers"
                        :type     "file"}]]]
             (let [input (.querySelector form "[type=file]")]
               (attach-files! input
                              [(fake-file "visa.txt") (fake-file "exit.txt")])
               (is (= ["visa.txt" "exit.txt"] (sut/bound-value input))))))

(deftest set-bound-value-clears-a-file-input-on-nil-only-test
  (with-html [form
              [:form
               [:input {:name "papers"
                        :type "file"}]]]
             (let [input (.querySelector form "[type=file]")]
               (attach-files! input [(fake-file "visa.txt")])
               (sut/set-bound-value! input "visa.txt")
               (is (= 1 (.-length (.-files input)))
                   "a non-nil write leaves the selection alone")
               (sut/set-bound-value! input nil)
               (is (zero? (.-length (.-files input)))))))

(deftest form->map-still-leaves-files-out-test
  (with-html [form
              [:form
               [:input {:name  "applicant"
                        :value "Ilsa Lund"}]
               [:input {:name "papers"
                        :type "file"}]]]
             (attach-files! (.querySelector form "[type=file]")
                            [(fake-file "visa.txt")])
             (is (= {:applicant "Ilsa Lund"} (sut/form->map form)))))
