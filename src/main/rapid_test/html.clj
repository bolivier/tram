(ns rapid-test.html
  "HTML testing utils inspired by Testing Library (https://testing-library.com/)"
  (:require [potemkin :refer [import-vars]]
            [rapid-test.html.role]))

;; Re-exports functions from other files

(import-vars [rapid-test.html.role get-by-role get-all-by-role])
