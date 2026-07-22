(ns rhizome.command)

(defmulti execute
  "Execute a rhizome command.  Takes 3 args, dispatches on `:op` in `cmd`.

  - `el` is the dom node the event is defined on 
  - `cmd` is the command being executed"
  (fn [el cmd] (:op cmd)))
