(ns dev.tailwind
  (:require
    [babashka.process :as p]))

(defn -main [& _]
  @(p/process {:out :inherit
               :error :inherit
               :dir "resources/tailwindcss"} "npm run dev"))
