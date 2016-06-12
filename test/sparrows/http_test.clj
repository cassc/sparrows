(ns sparrows.http-test
  (:require [sparrows.http :refer :all]
            [clojure.test :refer :all]))

(deftest a-test
  (testing "http"
    @(async-request {:url "https://192.168.0.72/v1/doc"
                     :method :head
                     :insecure? true
                     :headers {"content-type" "application/nippy"}})))
