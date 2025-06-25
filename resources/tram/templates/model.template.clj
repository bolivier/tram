(ns {{namespace}}
  (:require
   [tram.migrations :refer [serialize-to-sql write-to-migration-file delete-migration-file]]))


(def blueprint
  "This blueprint was created via the Tram cli.

  Instead of guessing syntax there to get things how you want them, just edit
   this directly and write and rewrite the migration file as needed."
  {{blueprint-string}})

(defn write  []
  (-> blueprint
      serialize-to-sql
      write-to-migration-file))

(defn erase []
  (delete-migration-file))

(comment

  ;; run this to re/write the migration file
  (write)

  ;; evaluate this to erase the migration file you created
  (erase)

  nil)
