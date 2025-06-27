(ns {{namespace}}
  (:require
   [tram.migrations :refer [write-to-migration-file delete-migration-file]]))


(def blueprint
  "This blueprint was created via the Tram cli.

  Instead of guessing syntax there to get things how you want them, just edit
   this directly and write and rewrite the migration file as needed."
  {{blueprint-string}})

(comment

  ;; run this to re/write the migration file
  (write-to-migration-file blueprint)

  ;; evaluate this to erase the migration file you created
  (delete-migration-file blueprint)

  nil)
