(ns sparrows.cypher-test
  (:require [sparrows.cypher :refer :all]
            [clojure.test :refer :all]))
(deftest cypher-test
  (testing "Testing cypher"
    (is (= "plaint text 中文"
           (aes-decrypt
            (aes-encrypt "plaint text 中文" "random-password")
            "random-password")))
    (is (= "plaint text 中文"
           (aes-decrypt
            (aes-encrypt "plaint text 中文" "random-password" {:as-hex 't})
            "random-password"
            {:as-hex 't})))
))
