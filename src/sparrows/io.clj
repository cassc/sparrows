(ns sparrows.io
  "This package provides common methods applicable for files
   and directories. `pack-dir` works only on Linux with 7z installed."
  (:require [sparrows.system :refer [file-separator linux? command-exists?]]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.edn :as e]
            [clojure.pprint :refer [pprint]])
  (:import [org.apache.commons.io FileUtils IOUtils]
           [java.io ByteArrayOutputStream InputStream]
           [java.util.zip GZIPInputStream GZIPOutputStream]))


(defn create-dir
  "Create directory with all parents if needed,
input could be the directory path as a string, or a file object.
May throw IOException when write failed."
  [dir]
  (let [f (if (string? dir) (io/file dir) dir)]
    (FileUtils/forceMkdir f)))

(defn get-name
  "Get the file name from a uri string"
  [^String uri]
  (last (.split  uri file-separator)))


(defn copy-dir
  "Target path including parent directories will be auto created."
  [^String from ^String to]
  (FileUtils/copyDirectory (io/file from) (io/file to) true))


(defn del-dir
  "Delete directory, will fail silently when `dir` does not
   exist, will throw an exception when deletion failed. "
  [dir]
  (FileUtils/deleteDirectory (io/file dir)))



(defn pack-dir
  "Create 7z archive of a directory, using command like
   `7z a -mhe=on -pmy_password archive.7z a_directory`

  `(pack-dir \"/tmp/pk\" \"./\" :password \"abc\" :filename \"abc\")`"
  [^String outdir dir & {:keys [password filename]}]
  (or (command-exists? "7z")
      (throw (Exception. "Archive command not exists!")))
  (let [source (io/file dir)
        _ (create-dir outdir)
        out (io/file outdir (str (or filename (.getName source)) ".7z"))]
    (if (.exists out)
      (throw (Exception. (str "Target File " out " already exists!")))
      (assert
       (= 0
          (:exit
           (if password
             (sh "7z" "a" "-mhe=on" (str "-p" password) (.getCanonicalPath out) dir)
             (sh "7z" "a" "-mhe=on" (.getCanonicalPath out) dir))))))))


(defn str->gzipped-bytes
  "Convert string to gzipped bytes"
  [^String str]
  (with-open [out (ByteArrayOutputStream.)
              gzip (GZIPOutputStream. out)]
    (do
      (.write gzip (.getBytes str "UTF-8"))
      (.finish gzip)
      (.toByteArray out))))

(defn str->gzipped-file
  "Convert intput string to gzipped file. The caller
   needs to manually delete the output file in case of
   exception."
  [^String str out]
  (with-open [out (io/output-stream out)
              gzip (GZIPOutputStream. out)]
    (do
      (.write gzip (.getBytes str "UTF-8"))
      (.finish gzip))))

(defn gzipped-input-stream->str
  [^InputStream input-stream & [^String encoding]]
  (with-open [out (ByteArrayOutputStream.)
              is  input-stream]
    (IOUtils/copy (GZIPInputStream. is) out)
    (.toString out (or encoding "utf8"))))


(defn read-props
  "Reads edn encoded `cfg` file. If this file does not exist, create it
  by loading `default-conf` if `create?` is true."
  [cfg  & [create? default-conf]]
  (if (.exists (io/file cfg))
    (e/read-string (slurp cfg))
    (when create?
      (prn  cfg "not found!")
      (with-open [o (io/writer cfg)]
        (prn "Creating default configuration: " cfg)
        (pprint default-conf o)
        default-conf))))
