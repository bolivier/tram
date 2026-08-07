#! /usr/bin/env bb
(ns dev.tailwind
  (:require [babashka.process :as p]))

@(p/process {:out :inherit
             :err :inherit
             :dir "resources/tailwindcss"}
            "npm i")
@(p/process {:out :inherit
             :err :inherit
             :dir "resources/tailwindcss"}
            "npm run dev")
