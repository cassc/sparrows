(ns sparrows.io-test
  (:require [sparrows.io :refer :all]
            [clojure.test :refer :all]))
(deftest a-test
  (testing "io"
    (comment
      (create-dir "/tmp/parent/will/be/auto/created")
      ; this will produce an IOException
      (create-dir "/this/should/fail")
      (copy-dir "/tmp/parent/" "/tmp/another/dir")
      (pack-dir "/tmp"  "/tmp/parent/" :password "me" :filename "mm" )
      )
    (= (get-name "this/is/a/file.name") "file.name")

    ))
