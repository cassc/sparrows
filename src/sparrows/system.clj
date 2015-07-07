(ns sparrows.system
  (:require [clojure.java.shell :refer [sh]])
  (:import [java.io File]
           [java.nio.file Files]))


(defn get-env
  "Get OS environmental variable."
  ([key]
     (.get (System/getenv) key))
  ([key default]
     (or (get-env key) default)))


(defn get-system-properties
  "Get system properties"
  []
  (System/getProperties))

(defn get-system-property
  [key]
  (System/getProperty key))


(def file-separator
  "OS-agnostic file separator "
  (File/separator))


(defn linux?
  "Is current app running on a linux box?"
  []
  (= "linux"
     (clojure.string/lower-case (get-system-property "os.name"))))

(defn command-exists?
  "Test if a command exists on linux"
  [command]
  (and (linux?) (= 0 (:exit (sh "which" command)))
       command))

(defn get-mime
  "Get MIME file type. Input could be a string or a File object"
  [f]
  (let [^java.io.File file (if (string? f)
                             (clojure.java.io/file f)
                             f)]
    (when (and file (.exists file))
      (Files/probeContentType (.toPath file)))))


(defn register-shutdownhook
  "Add func as a shutdownhook. This function will be executed when JVM
  termination signal received."
  [func & args]
  (.addShutdownHook
   (Runtime/getRuntime)
   (Thread.
    #(apply func args))))
