(ns tram-docs.concerns.active-search-example
  (:require [clojure.string :as str]))

(def squadron
  "Flight roster of the 256th, stationed on Pianosa."
  [{:name "John Yossarian"
    :rank "Captain"
    :duty "Bombardier"}
   {:name "Milo Minderbinder"
    :rank "Lieutenant"
    :duty "Mess officer"}
   {:name "Major Major Major Major"
    :rank "Major"
    :duty "Squadron commander"}
   {:name "Colonel Cathcart"
    :rank "Colonel"
    :duty "Group commander"}
   {:name "Colonel Korn"
    :rank "Colonel"
    :duty "Executive officer"}
   {:name "Chaplain Tappman"
    :rank "Captain"
    :duty "Chaplain"}
   {:name "Doc Daneeka"
    :rank "Captain"
    :duty "Flight surgeon"}
   {:name "Orr"
    :rank "Lieutenant"
    :duty "Pilot"}
   {:name "Nately"
    :rank "Lieutenant"
    :duty "Pilot"}
   {:name "McWatt"
    :rank "Captain"
    :duty "Pilot"}
   {:name "Dunbar"
    :rank "Lieutenant"
    :duty "Bombardier"}
   {:name "Clevinger"
    :rank "Lieutenant"
    :duty "Bombardier"}
   {:name "Havermeyer"
    :rank "Captain"
    :duty "Lead bombardier"}
   {:name "Aarfy Aardvark"
    :rank "Captain"
    :duty "Navigator"}
   {:name "Hungry Joe"
    :rank "Lieutenant"
    :duty "Pilot"}
   {:name "Snowden"
    :rank "Sergeant"
    :duty "Gunner"}
   {:name "Chief White Halfoat"
    :rank "Captain"
    :duty "Assistant intelligence officer"}
   {:name "Captain Black"
    :rank "Captain"
    :duty "Intelligence officer"}
   {:name "Nurse Duckett"
    :rank "Lieutenant"
    :duty "Nurse"}
   {:name "Major Danby"
    :rank "Major"
    :duty "Group operations officer"}
   {:name "Ex-P.F.C. Wintergreen"
    :rank "Private"
    :duty "Mail clerk"}
   {:name "General Dreedle"
    :rank "General"
    :duty "Wing commander"}])

(defn search
  "Squadron members whose name, rank, or duty contains `query`.

  A blank query matches everyone."
  [query]
  (if (str/blank? query)
    squadron
    (let [needle (str/lower-case query)]
      (filter (fn [member]
                (some #(str/includes? (str/lower-case %)
                                      needle)
                      ((juxt :name
                             :rank
                             :duty)
                        member)))
        squadron))))
