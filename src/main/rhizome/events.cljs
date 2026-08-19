(ns rhizome.events)

(def events
  "Map of rhizome event keywords to native DOM event-type strings passed to
  `addEventListener`."
  {;; mouse
   :event/click       "click"
   :event/dblclick    "dblclick"
   :event/mousedown   "mousedown"
   :event/mouseup     "mouseup"
   :event/mouseenter  "mouseenter"
   :event/mouseleave  "mouseleave"
   :event/mouseover   "mouseover"
   :event/mouseout    "mouseout"
   :event/mousemove   "mousemove"
   :event/contextmenu "contextmenu"
   :event/drop        "drop"
   :event/dragenter   "dragenter"
   :event/dragleave   "dragleave"
   ;; keyboard
   :event/keydown     "keydown"
   :event/keyup       "keyup"
   :event/keypress    "keypress"
   ;; form
   :event/submit      "submit"
   :event/change      "change"
   :event/input       "input"
   :event/focus       "focus"
   :event/blur        "blur"
   :event/focusin     "focusin"
   :event/focusout    "focusout"
   :event/reset       "reset"
   :event/select      "select"
   ;; clipboard
   :event/copy        "copy"
   :event/cut         "cut"
   :event/paste       "paste"
   ;; window / misc
   :event/scroll      "scroll"
   :event/resize      "resize"
   :event/load        "load"})

(defn ->dom-event [event-kw]
  (or (get events event-kw) (name event-kw)))
