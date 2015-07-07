(defproject sparrows "0.1.8"
  :description "A utility library providing encryption/decryption, io utils and more."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-http "0.9.2"
                  :exclusions [cheshire crouton ]]
                 [commons-codec "1.9"]
                 [antler/commons-io "2.2.0"]
                 [zololabs/jericho-html-parser "3.3.0"]
                 [com.taoensso/timbre "3.1.6"]
                 [org.apache.commons/commons-email "1.2"]]
  :omit-source false
  :global-vars {*warn-on-reflection* true}
  :java-source-paths ["src/java"])
