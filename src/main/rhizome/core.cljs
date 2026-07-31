(ns rhizome.core
  (:require [clojure.edn :as edn]
            [rhizome.dom :as dom]
            [rhizome.triggers :as triggers]))

(defn mount!
  ([]
   (mount! js/document))
  ([root]
   (doseq [[trigger config] @triggers/registry]
     (doseq [el (dom/query-selector-triggers root trigger)]
       (when-not (unchecked-get el
                                "rhizomeWired")
         (unchecked-set el
                        "rhizomeWired"
                        true)
         (let [{:keys [on-mount listener default-event]} config
               directive (try
                           (edn/read-string (get el
                                                 trigger))
                           (catch js/Error _
                             (println "Could not parse edn"
                                      (str get
                                           el
                                           trigger))
                             nil))]
           (when on-mount
             (on-mount directive
                       el))
           (when default-event
             (dom/add-event-listener el
                                     default-event
                                     (fn [e]
                                       (listener e
                                                 directive
                                                 el))))))))))

(defn init []
  (triggers/register! triggers/bind)
  (triggers/register! triggers/text)
  (mount!))
