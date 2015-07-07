(ns sparrows.misc
  (:require
   [clojure.zip :as zip]
   [clojure.xml :as xml]
   [taoensso.timbre :as timbre]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as s]

   [sparrows.system :refer [command-exists?]])
  (:import
   [net.htmlparser.jericho Source TextExtractor]
   [java.util TimeZone Date]
   [java.text SimpleDateFormat]
   [java.io BufferedReader StringReader]))

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
  [m]
  (loop [ks (keys m)
         m m]
    (if (seq ks)
      (if-not (get m (first ks))
        (recur (rest ks) (dissoc m (first ks)))
        (recur (rest ks) m))
      m)))
