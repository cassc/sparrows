(ns sparrows.http-test
  (:require [sparrows.http :refer :all]
            [clojure.test :refer :all]))

(deftest a-test
  (testing "http"
    (comment
      (GET "http://www.hipda.com")
)

    ))
