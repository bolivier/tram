(ns rhizome.core
  "Public api of the rhizome client runtime: default-config, register,
  start!, run!. Rebuilt from scratch per ADR-0005; the api lands with
  phase 1 of docs/plans/rhizome-impl-plan.md.")

(defn init
  "Entry point for the drop-in script and the docsite build. Becomes
  (start! (default-config)) when phase 1 lands."
  [])
