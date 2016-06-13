(defproject org.clojars.august/sparrows "0.2.6-SNAPSHOT"
  :description "A utility library providing encryption/decryption, io utils and more."
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [http-kit "2.1.19" :exclusions [org.clojure/clojure]]
                 [clj-http "2.2.0" :exclusions [cheshire crouton ]]
                 [com.climate/claypoole "1.1.2"]
                 [commons-codec "1.10"]
                 [commons-io/commons-io "2.4"]
                 ;; [zololabs/jericho-html-parser "3.3.0"]
                 [com.taoensso/timbre "4.3.1"]
                 [org.apache.commons/commons-email "1.4"]]
  :omit-source false
  :javac-options ["-target" "1.8" "-source" "1.8"] ;;  "-Xlint:-options"
  :jvm-opts ["-Dfile.encoding=UTF-8"]
  :global-vars {*warn-on-reflection* false}
  :java-source-paths ["src/java"])
