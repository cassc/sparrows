(ns sparrows.time
  (:require [sparrows.misc :as misc])
  (:import [java.time ZoneId Instant LocalDateTime ZonedDateTime LocalDate]
           [java.time.temporal WeekFields]
           [java.util Locale]
           [java.sql Timestamp]
           [java.time.format DateTimeFormatter]))

(def date-string-pattern "yyyy-MM-dd")
(def datetime-string-pattern "yyyy-MM-dd HH:mm:ss")


(defn zone-by-offset [^String offset]
  (ZoneId/of offset))

(def bj-zone (zone-by-offset "+8"))

(defn make-formatter
  "Make a DateTimeFormatter instance. Default timezone offset is +8"
  [{:keys [^String pattern ^String offset]}]
  (let [dtf (DateTimeFormatter/ofPattern pattern)]
    (if offset
      (.withZone dtf (zone-by-offset offset))
      dtf)))


(defn string->long
  "Convert string to epoch millis. 

  - If pattern does not contain timezone, offset must be provided.
  - If pattern contains timezone info(Z/z/X), offset must be consistant with the offset sepcified in the input string `s`.
  - If no hour of day is specified, 0-clock is assumed."
  [s {:keys [pattern offset]}]
  ;; TODO check for precondition
  (let [fmt (make-formatter {:pattern pattern :offset offset})
        with-hour? (.contains pattern "H")
        zdt (if offset
              (let [local-time (if with-hour?
                                 (LocalDateTime/parse s fmt)
                                 (.atStartOfDay (LocalDate/parse s fmt)))]
                (.atZone local-time (zone-by-offset offset)))
              (ZonedDateTime/parse s fmt))]
    (.. zdt toInstant toEpochMilli)))

(defn long->string
  "Convert epoch millis to the string representation with the
  `pattern` in the timezone `offset`"
  [ts {:keys [pattern offset]}]
  {:pre [pattern]}
  (let [fmt (make-formatter {:pattern pattern :offset offset})
        ts (misc/str->num ts)]
    (.format fmt (Instant/ofEpochMilli ts))))

(defmacro def-string-long-conversion [time-type default-pattern]
  `(do
     (defn ~(symbol (str "long->" time-type "-string"))
       ~(str "Convert epoch time in millis to " time-type " string ")
       ([ts#]
        (long->string ts# {:pattern ~default-pattern :offset "+8"}))
       ([ts# options#]
        (long->string ts# (assoc options# :pattern (or (:pattern options#) ~default-pattern)))))
     (defn ~(symbol (str time-type "-string->long"))
       ~(str "Convert " time-type " string to epoch time in millis. If pattern contains timezone info, offset should be consistant with the input string.")
       ([s#]
        (string->long s# {:pattern ~default-pattern :offset "+8"}))
       ([s# options#]
        (string->long s# (assoc options# :pattern (or (:pattern options#) ~default-pattern)))))))

;; defines date-string, datetime-string to/from long conversion functions
(def-string-long-conversion date date-string-pattern)
(def-string-long-conversion datetime datetime-string-pattern)

(defn now
  "Returns an Instant object representing current time"
  []
  (Instant/now))

(defn now-in-nanos []
  (.getNano (now)))

(defn now-in-millis []
  (.toEpochMilli (now)))

(defn now-in-secs []
  (.getEpochSecond (now)))

(defn start-of-week
  "Calculate epoch millis at the start of the week."
  ([]
   (start-of-week nil))
  ([ts]
   (start-of-week ts nil))
  ([ts {:keys [zone locale week-start]
        :or {zone bj-zone
             locale Locale/US
             week-start 1}}]
   (let [instant (if ts (Instant/ofEpochMilli ts) (now))
         ldt     (LocalDateTime/ofInstant instant zone)
         w-start (.with
                  ldt
                  (.dayOfWeek (WeekFields/of locale))
                  week-start)
         millis  (.. w-start
                     toLocalDate
                     (atStartOfDay zone)
                     toInstant
                     toEpochMilli)]
     millis)))

(defn start-of-day
  "Calculate epoch millis at the start of the next day. If no `zone`
  is provided, `bj-zone` is assumed."
  ([]
   (start-of-day nil))
  ([ts]
   (start-of-day ts nil))
  ([ts {:keys [zone]
        :or {zone bj-zone}}]
   (let [instant (if ts (Instant/ofEpochMilli ts) (now))
         ldt     (LocalDateTime/ofInstant instant zone)]
     (.. ldt toLocalDate (atStartOfDay zone) toInstant toEpochMilli))))

(defn start-of-next-day
  "Calculate epoch millis at the start of the day"
  ([]
   (start-of-next-day nil))
  ([ts]
   (start-of-next-day ts nil))
  ([ts {:keys [zone]
        :or {zone bj-zone}}]
   (let [instant (if ts (Instant/ofEpochMilli ts) (now))
         ldt     (LocalDateTime/ofInstant (.plusMillis instant 86400000) zone)]
     (.. ldt toLocalDate (atStartOfDay zone) toInstant toEpochMilli))))

(defn to-sql-time [ts]
  (java.sql.Timestamp. ts))

(defn from-sql-time [sql-date]
  (.getTime sql-date))


