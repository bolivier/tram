(ns rhizome.dom-test
  (:require [cljs.test :refer [deftest is]]
            [rhizome.dom :as sut]
            [rhizome.test-utils :refer [attach-files! fake-file]]
            [shadow.cljs.modern :refer [defclass]])
  (:require-macros [rhizome.macros :refer [with-html]]))

(defclass FormAssociatedControl
          (extends js/HTMLElement)
          (constructor [this] (super)))

(set! (.-formAssociated FormAssociatedControl) true)

(js/Object.defineProperty
  (.-prototype FormAssociatedControl)
  "value"
  #js {:get (fn [] (this-as this (.getAttribute this "value")))})

(when-not (js/customElements.get "form-associated-control")
  (js/customElements.define "form-associated-control"
                            FormAssociatedControl))

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

(deftest form->map-reads-an-unchecked-valued-box-as-nil-test
  (with-html [form
              [:form
               [:input {:name  "papers"
                        :type  "checkbox"
                        :value "forged"}]
               [:input {:checked true
                        :name    "visa"
                        :type    "checkbox"
                        :value   "granted"}]]]
             (is (= {:papers nil
                     :visa   "granted"}
                    (sut/form->map form)))))

(deftest form->map-reads-a-valueless-box-as-a-boolean-test
  (with-html [form
              [:form
               [:input {:checked true
                        :name    "sober"
                        :type    "checkbox"}]
               [:input {:name "armed"
                        :type "checkbox"}]]]
             (is (= {:sober true
                     :armed false}
                    (sut/form->map form)))))

(deftest form->map-reads-a-radio-group-as-its-checked-value-test
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

(deftest form->map-reads-an-unchecked-radio-group-as-nil-test
  (with-html [form
              [:form
               [:input {:name  "flight"
                        :type  "radio"
                        :value "Berlin"}]
               [:input {:name  "flight"
                        :type  "radio"
                        :value "Lisbon"}]]]
             (is (= {:flight nil} (sut/form->map form)))))

(deftest form->map-collects-a-vector-in-dom-order-test
  (with-html [form
              [:form
               [:input {:checked           true
                        :name              "crew"
                        :rhizome.core/type ":vector"
                        :type              "checkbox"
                        :value             "Rick"}]
               [:input {:name              "crew"
                        :rhizome.core/type ":vector"
                        :type              "checkbox"
                        :value             "Ugarte"}]
               [:input {:checked           true
                        :name              "crew"
                        :rhizome.core/type ":vector"
                        :type              "checkbox"
                        :value             "Sam"}]]]
             (is (= {:crew ["Rick" "Sam"]} (sut/form->map form)))))

(deftest form->map-collects-one-control-into-a-vector-test
  (with-html [form
              [:form
               [:input {:name              "stops"
                        :rhizome.core/type "[:vector :int]"
                        :value             "5"}]]]
             (is (= {:stops [5]} (sut/form->map form)))))

(deftest form->map-collects-a-set-test
  (with-html [form
              [:form
               [:input {:checked           true
                        :name              "crew"
                        :rhizome.core/type ":set"
                        :type              "checkbox"
                        :value             "Rick"}]
               [:input {:name              "crew"
                        :rhizome.core/type ":set"
                        :type              "checkbox"
                        :value             "Sam"}]]]
             (is (= {:crew #{"Rick"}} (sut/form->map form)))))

(deftest form->map-collects-nothing-checked-into-an-empty-set-test
  (with-html [form
              [:form
               [:input {:name              "crew"
                        :rhizome.core/type ":set"
                        :type              "checkbox"
                        :value             "Rick"}]]]
             (is (= {:crew #{}} (sut/form->map form)))))

(deftest form->map-collects-a-set-of-ints-test
  (with-html [form
              [:form
               [:input {:checked           true
                        :name              "label-ids"
                        :rhizome.core/type "[:set :int]"
                        :type              "checkbox"
                        :value             "1"}]
               [:input {:checked           true
                        :name              "label-ids"
                        :rhizome.core/type "[:set :int]"
                        :type              "checkbox"
                        :value             "3"}]]]
             (is (= {:label-ids #{1 3}} (sut/form->map form)))))

(deftest form->map-casts-a-select-to-an-int-test
  (with-html [form
              [:form
               [:select {:name              "status-id"
                         :rhizome.core/type ":int"}
                [:option {:value "4"} "To do"]
                [:option {:selected true
                          :value    "12"}
                 "Done"]]]]
             (is (= {:status-id 12} (sut/form->map form)))))

(deftest form->map-casts-a-blank-int-to-nil-test
  (with-html [form
              [:form
               [:input {:name              "estimate"
                        :rhizome.core/type ":int"
                        :value             ""}]]]
             (is (= {:estimate nil} (sut/form->map form)))))

(deftest form->map-passes-an-uncastable-int-unchanged-test
  (with-html [form
              [:form
               [:input {:name              "estimate"
                        :rhizome.core/type ":int"
                        :value             "12a"}]
               [:input {:name              "ledger-id"
                        :rhizome.core/type ":int"
                        :value             "9007199254740993"}]
               [:input {:name              "fare"
                        :rhizome.core/type ":int"
                        :type              "number"
                        :value             "1.5"}]]]
             (is (= {:estimate  "12a"
                     :ledger-id "9007199254740993"
                     :fare      1.5}
                    (sut/form->map form)))))

(deftest form->map-casts-a-multi-select-to-ints-test
  (with-html [form
              [:form
               [:select {:multiple          true
                         :name              "gate-ids"
                         :rhizome.core/type ":int"}
                [:option {:selected true
                          :value    "2"}
                 "Gate 2"]
                [:option {:selected true
                          :value    "7"}
                 "Gate 7"]]]]
             (is (= {:gate-ids [2 7]} (sut/form->map form)))))

(deftest form->map-collects-a-multi-select-into-a-set-test
  (with-html [form
              [:form
               [:select {:multiple          true
                         :name              "cargo"
                         :rhizome.core/type ":set"}
                [:option {:selected true
                          :value    "letters"}
                 "letters"]
                [:option {:value "gin"}
                 "gin"]]]]
             (is (= {:cargo #{"letters"}} (sut/form->map form)))))

(deftest form->map-throws-on-a-repeated-untyped-name-test
  (with-html [form
              [:form
               [:input {:name  "crew"
                        :value "Rick"}]
               [:input {:name  "crew"
                        :value "Sam"}]]]
             (is (thrown? js/Error (sut/form->map form)))))

(deftest form->map-throws-on-mismatched-types-test
  (with-html [form
              [:form
               [:input {:name              "crew"
                        :rhizome.core/type ":vector"
                        :value             "Rick"}]
               [:input {:name              "crew"
                        :rhizome.core/type ":set"
                        :value             "Sam"}]]]
             (is (thrown? js/Error (sut/form->map form)))))

(deftest form->map-throws-on-an-unknown-type-test
  (with-html [form
              [:form
               [:input {:name              "crew"
                        :rhizome.core/type ":list"
                        :value             "Rick"}]]]
             (is (thrown? js/Error (sut/form->map form)))))

(deftest form->map-throws-on-a-name-edn-cannot-read-back-test
  (with-html [form
              [:form
               [:input {:name  "tags[]"
                        :value "contraband"}]]]
             (is (thrown? js/Error (sut/form->map form)))))

(deftest form->map-skips-controls-in-a-disabled-fieldset-test
  (with-html [form
              [:form
               [:fieldset {:disabled true}
                [:input {:name  "applicant"
                         :value "Ugarte"}]]
               [:input {:name  "destination"
                        :value "Lisbon"}]]]
             (is (= {:destination "Lisbon"} (sut/form->map form)))))

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

(deftest form->map-reads-numeric-inputs-as-numbers-test
  (with-html [form
              [:form
               [:input {:name  "passengers"
                        :type  "number"
                        :value "2"}]
               [:input {:name  "fare"
                        :type  "number"
                        :value "12.5"}]
               [:input {:name  "priority"
                        :type  "range"
                        :value "7"}]]]
             (is (= {:passengers 2
                     :fare       12.5
                     :priority   7}
                    (sut/form->map form)))))

(deftest form->map-reads-an-empty-number-input-as-nil-test
  (with-html [form
              [:form
               [:input {:name "passengers"
                        :type "number"}]]]
             (is (= {:passengers nil} (sut/form->map form)))))

(deftest form->map-includes-controls-tied-by-form-attribute-test
  (with-html [root
              [:div
               [:form#transit]
               [:input {:form  "transit"
                        :name  "applicant"
                        :value "Victor Laszlo"}]]]
             (is (= {:applicant "Victor Laszlo"}
                    (sut/form->map (.querySelector root "form"))))))

(deftest form->map-reads-form-associated-custom-elements-test
  (with-html [form
              [:form
               [:form-associated-control {:name  "destination"
                                          :value "Lisbon"}]]]
             (is (= {:destination "Lisbon"} (sut/form->map form)))))

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
