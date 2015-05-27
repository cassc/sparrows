(ns sparrows.misc-test
  (:require [sparrows.misc :refer :all]
            [clojure.test :refer :all]))

(deftest a-test
  (testing "misc"
    ((wrap-exception int (fn [e] (prn (str "This is an intended exception"  e)))) "This is an expected exception.")))
