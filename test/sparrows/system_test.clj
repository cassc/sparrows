(ns sparrows.system-test
  (:require [sparrows.system :refer :all]
            [clojure.test :refer :all]))


(deftest a-test
  (testing "system"
    (get-env "PATH")
    (get-system-property "os.name")
    (get-system-properties)
    (get (get-system-properties) "java.version")))
