(ns sparrows.time-test
  (:require [sparrows.time :refer :all]
            [clojure.test :refer :all]))


(deftest time-test
  (testing "Testing time ns"
    (is (= (long->date-string 1465713866245)
           "2016-06-12"))
    (is (= (long->date-string 1465713866245 {:pattern "yyyy-MM-dd" :offset "+8"})
           "2016-06-12"))
    (is (= (long->datetime-string 1465713866245 {:pattern "yyyy-MM-dd HH:mm:ss.S Z" :offset "+8"})
           "2016-06-12 14:44:26.2 +0800"))
    (is (= (datetime-string->long "2016-06-12 13:44:33")
           1465710273000))
    (is (= (datetime-string->long "2016-06-12 13:44:33 +0100" {:pattern "yyyy-MM-dd HH:mm:ss Z"})
           (+ 1465710273000 (* 7 3600 1000))))))
