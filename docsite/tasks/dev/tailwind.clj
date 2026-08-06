(ns dev.tailwind
  (:require [babashka.process :as p]))

(defn -main [& _]
  #_@(p/process {:out :inherit
                 :err :inherit
                 :dir "resources/tailwindcss"}
                "npm i")
  #_@(p/process {:out :inherit
                 :err :inherit
                 :dir "resources/tailwindcss"}
                "npm run dev"))
