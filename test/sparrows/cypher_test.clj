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
            {:as-hex 't}))))
  (testing "Testing base64/form-encode/decode and compress string"
    (let [t "<vitacat> <type>2</type> <data class=\"map\"> <entry> <string>xdgs</string> <long>0</long> </entry> <entry> <string>process</string> <string>end</string> </entry> <entry> <string>ets</string> <long>1417616573941</long> </entry> <entry> <string>dxxl</string> <long>1</long> </entry> <entry> <string>tired</string> <long>5</long> </entry> <entry> <string>hr</string> <long>60</long> </entry> <entry> <string>pid</string> <long>10071</long> </entry> <entry> <string>jsyl</string> <long>50</long> </entry> <entry> <string>sts</string> <long>1417616423941</long> </entry> <entry> <string>ylzs</string> <long>37</long> </entry> <entry> <string>xdgh</string> <long>0</long> </entry> <entry> <string>xlbq</string> <long>0</long> </entry> <entry> <string>category</string> <string>he</string> </entry> <entry> <string>device</string> <string>t2</string> </entry> <entry> <string>kynl</string> <long>-100</long> </entry> <entry> <string>isnormal</string> <long>1</long> </entry> </data> <from>user-eeak</from> <fromOfname>user-eeak</fromOfname> </vitacat> </body>"]
      (is (= t (decompress-string (compress-string t))))
      (is (= t (form-decode (form-encode t))))
      (is (= t (base64-decode (base64-encode t))))
      (is (= t (base64-decode (base64-encode t :as-bytes? true))))))
  )
