(ns tram.utils.time
  (:import java.text.SimpleDateFormat
           [java.util Date TimeZone]))

(defn timestamp []
  (let [fmt (doto (SimpleDateFormat. "yyyyMMddHHmmss")
              (.setTimeZone (TimeZone/getTimeZone "UTC")))]
    (.format fmt (Date.))))
