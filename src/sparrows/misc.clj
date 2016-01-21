(ns sparrows.misc
  (:require [clojure
             [string :as s]
             [xml :as xml]
             [zip :as zip]]
            [clojure.java.shell :refer [sh]]
            [sparrows.system :refer [command-exists?]]
            [taoensso.timbre :as timbre])
  (:import [java.io BufferedReader StringReader]
           java.text.SimpleDateFormat
           [java.util Date TimeZone UUID]
           [net.htmlparser.jericho Source TextExtractor]))

(timbre/refer-timbre)

(defn wrap-exception
  "Wrap exception with an optional `call-back` function.
   The call-back function accepts the exception `e` and
   the inputs of `func` as args. Returns nil if no
   call-back is provide."
  [func & [call-back]]
  (fn [& args]
    (try
      (apply func args)
      (catch Exception e
        (warn e (.getMessage e))
        (if call-back
          (apply call-back e args))))))

(defn extract-text
  "Given a string source, returns the extracted text content"
  [^String s]
  (let [source  (Source. s)]
    (.toString (TextExtractor. source))))


(defn zip-str
  "convenience function to parse xml string as clojure datastructure, first seen at nakkaya.com later in clj.zip src"
  [^String s]
  (zip/xml-zip
   (xml/parse (java.io.ByteArrayInputStream. (.getBytes s "UTF-8")))))



(defn get-time-as-string
  "Convert epoch time in millis to string by wrapping around
   SimpleDateFormat. `timezone` should be like `GMT-8` or `GMT+8`.
   Note that if no `timezone` is specified, GMT+8 will be employed."
  [time-millis & {:keys [format ^String timezone] :or {format "yyyy/MM/dd HH:mm:ss" timezone "GMT+8"}}]
  (let [d (Date. ^long time-millis)
        sdf (doto (SimpleDateFormat. format)
              (.setTimeZone (TimeZone/getTimeZone timezone)))]
    (.format sdf d)))


(defn if-and-let*
  [bindings then-clause else-clause deshadower]
  (if (empty? bindings)
    then-clause
    `(if-let ~(vec (take 2 bindings))
       ~(if-and-let* (drop 2 bindings) then-clause else-clause deshadower)
       (let ~(vec (apply concat deshadower))
         ~else-clause))))

(defmacro if-and-let
  "Like if-let, but with multiple bindings allowed. If all of the
  expressions in the bindings evaluate truthy, the then-clause is
  executed with all of the bindings in effect. If any of the expressions
  evaluates falsey,evaluation of the remaining binding exprs is not
  done, and the else-clause is executed with none of the bindings in
  effect. If else-clause is omitted, evaluates to nil if any of the
  binding expressions evaluates falsey. As with normal let bindings,
  each binding is available in the subsequent
  bindings. (if-and-let [a (get my-map :thing) b (do-thing-with a)] ...)
  is legal, and will not throw a null pointer exception if my-map lacks
  a :thing key and (do-thing-with nil) would throw an NPE. If there's
  something you want to be part of the then-clause's condition,but whose
  value you don't care about, including a binding of it to _ is more
  compact than nesting yet another if inside the then-clause.
  Example:
    (if-and-let [x (:a {:a 42}) y (first [(/ x 3)])] [x y] :nothing)"
  ([bindings then-clause]
     `(if-and-let ~bindings ~then-clause nil))
  ([bindings then-clause else-clause]
     (let [shadowed-syms (filter #(or ((or &env {}) %) (resolve %))
                                 (filter symbol?
                                         (tree-seq coll? seq (take-nth 2 bindings))))
           deshadower (zipmap shadowed-syms (repeatedly gensym))]
       `(let ~(vec (apply concat (map (fn [[k v]] [v k]) deshadower)))
          ~(if-and-let* bindings then-clause else-clause deshadower)))))



; Extract
;  package: name, versionCode, versionName
;  sdkVersion
;  application-label

(defn- strip-apostrophe
  [^String s]
  (if (string? s)
    (let [s (if (.startsWith s "'") (subs s 1) s)]
      (if (.endsWith s "'")
        (subs s 0 (dec (count s)))
        s))
    s))


(defn- parse-package-value
  "Parse value of package"
  [pv]
  (reduce
   #(assoc % (first %2) (second %2))
   {}
   (for [kvs (s/split pv #"\s+")
         :let [[k v] (s/split kvs #"=")]
         :when (not (s/blank? k))]
     [(keyword k) (strip-apostrophe v)])))

(defn- manifest-line-parser
  "Parse a line of manifest xml"
  [line]
  (let [[k v] (s/split line #":")
        v (if (= k "package") (parse-package-value v) v)
        k (keyword k)]
    {k (strip-apostrophe v)}))


(defn get-apk-meta-info
  "Get meta info as a seq of map from an apk. Requires aapt command on
  Linux system. `select-keys` is vector which can be used to filter
  entries to be returned."
  [apk & [select-keys]]
  (if-not (command-exists? "aapt")
    (throw (RuntimeException. "Command not found, please add aapt in your PATH."))
    (let [resp (sh "aapt" "dump" "badging" apk)
          lines (line-seq (BufferedReader. (StringReader. (:out resp))))
          props (map manifest-line-parser lines)]
      (if (seq select-keys)
        (filter #(some (partial  = (first (keys %)) ) select-keys) props)
        props))))



;; String ops
(defn trim
  "Trims a string, returns nil if the string is blank. "
  [input]
  (if-not (s/blank? input)
    (try
      (s/trim input)
      (catch Exception e))))



(defn lowercase-trim
  "Convert to lowercase and trim. Returns nil if any exception occurs."
  [str]
  (try
    (s/lower-case (trim str))
    (catch Exception e)))

(defn str->num
  "String to number. If input is already a number, returns
  itself. Returns nil if input is not a number."
  [n]
  (if (number? n)
    n
    (try
      (let [r (read-string n)]
        (if (number? r) r))
      (catch Exception e))))

(defn dissoc-nil-val
  "Remove all entries with nil val"
  {:deprecated "0.2.1"}
  [m]
  (loop [ks (keys m)
         m m]
    (if (seq ks)
      (if-not (get m (first ks))
        (recur (rest ks) (dissoc m (first ks)))
        (recur (rest ks) m))
      m)))

(defn dissoc-empty-val
  "Remove all entries with nil or empty val"
  [m]
  (loop [ks (keys m)
         m m]
    (if (seq ks)
      (let [v (get m (first ks))]
        (if (cond
              (string? v)          (lowercase-trim v)
              (instance? Number v) v
              (sequential? v)      (seq v)
              :else                v)
          (recur (rest ks) m)
          (recur (rest ks) (dissoc m (first ks)))))
      m)))

(defn uuid
  "Return uuid without hyphens"
  []
  (.. UUID randomUUID toString (replace "-" "")))


(defn now-millis
  []
  (System/currentTimeMillis))

(defn now-nanos
  []
  (System/nanoTime))


(defn super-of?
  "Determines if `p` is a super class/interface of `c`.

  Usage
  `(super-of? Throwable (.getClass e#))`"
  [p c]
  (.isAssignableFrom ^Class p ^Class c))


(defn wrap-time
  "Returns a wrapped function of the original, logs execution time of
  this function if :enable-time-logging is enabled. "
  ([f]
   (wrap-time f f))
  ([key f]
   (fn [& args]
     (let [start (System/currentTimeMillis)]
       (try
         (apply f args)
         (finally
           (info "Runtime of" (-> #'key meta :name) (- (System/currentTimeMillis) start))))))))

(defmacro wrap-nil-on-error
  "Eval (list* func args) in a try catch block. If any error is caught,
  returns nil"
  [& body]
  `(try
     ~@body
     (catch Throwable e#
       (warn e# "error ignored")
       nil)))

(defn- int-entry-to-map
  [m [k v]]
  (assoc m k (or (str->num v) v)))

(defn maybe-vals->int
  "Convert all values in a map to number if possible."
  [m]
  (reduce int-entry-to-map {} m))

(defn rand-ints
  "Return a string with `n` random digits"
  [n]
  (reduce str (repeatedly n #(rand-int 10))))

(defn num=
  "Returns true if every argument is approximately equal. Converts string to number automatically."
  [& args]
  (try
    (let [[f & rs] (map str->num args)]
      (every? #(< (Math/abs %) 0.00001) (map #(- f %) rs)))
    (catch Exception e
      (debug (.getMessage e))
      nil)))
