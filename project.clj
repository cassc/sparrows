(defproject org.clojars.august/sparrows "0.2.8"
  :description "A utility library providing encryption/decryption, io utils and more."
  :url "http://www.eclipse.org/legal/epl-v10.html"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [http-kit "2.2.0" :exclusions [org.clojure/clojure]]
                 [clj-http "3.7.0" :exclusions [cheshire crouton ]]
                 [com.climate/claypoole "1.1.4"]
                 [commons-codec "1.11"]
                 [commons-io/commons-io "2.6"]
                 [com.rpl/specter "1.0.5"]
                 [com.taoensso/timbre "4.10.0"]
                 [org.apache.commons/commons-email "1.5"]]
  :omit-source false
  :javac-options ["-target" "1.8" "-source" "1.8"] ;;  "-Xlint:-options"
  :jvm-opts ["-Dfile.encoding=UTF-8"]
  :global-vars {*warn-on-reflection* false}
  :java-source-paths ["src/java"])
