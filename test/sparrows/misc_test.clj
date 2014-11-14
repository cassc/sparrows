(ns sparrows.misc-test
  (:require [sparrows.misc :refer :all]
            [clojure.test :refer :all]))

(deftest a-test
  (testing "misc"
    ((wrap-exception int) "This is an expected exception.")


    ))
