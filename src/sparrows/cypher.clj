(ns sparrows.cypher
  "The following hash functions are provided by this namespace:
    - md5
    - sha1
    - sha256
    - sha512
    - 'www-form-urlencoded' encoding scheme: form-encode and form-decode
    - base64
    - aes (may require installing unlimited JCE extension for JVM, see http://stackoverflow.com/questions/6481627/java-security-illegal-key-size-or-default-parameters/6481658#6481658"
  (:require [clojure.java.io :as io])
  (:import [org.apache.commons.codec.digest DigestUtils]
           [org.apache.commons.codec.net URLCodec]
           [org.apache.commons.codec.binary Base64 Hex]

           [java.security AlgorithmParameters SecureRandom]
           [javax.crypto BadPaddingException Cipher IllegalBlockSizeException SecretKey SecretKeyFactory]
           [javax.crypto.spec IvParameterSpec PBEKeySpec SecretKeySpec]

           [java.io ByteArrayInputStream ByteArrayOutputStream]
           [third AESCrypt]))

; salt length
(def SALT_LEN 20)
; ivbytes length
(def IVBYTE_LEN 16)

(defonce url-encoder
  (URLCodec. "utf8"))

(defn rand-str
  "Generate a random alpha-numeric string with length n"
  [& [n]]
  (let [pw-chars "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        rand-chars (repeatedly (or n 6) #(rand-nth pw-chars))]
    (apply str rand-chars)))


(defn get-bytes
  "Get bytes array using utf8"
  [str]
  (.getBytes str "utf8"))



(defn md5
  "Returns MD5 hash as string"
  [in & [{:keys [as-bytes]}]]
  (if as-bytes
    (DigestUtils/md5 in)
    (DigestUtils/md5Hex in)))


(defn sha512
  [in]
  (DigestUtils/sha512Hex in))

(defn sha256
  [in]
  (DigestUtils/sha256Hex in))

(defn sha1
  [in]
  (DigestUtils/sha1Hex in))

(defn form-encode
  "Encodes a string into its URL safe form using utf8"
  [str]
  (.encode url-encoder str "utf8"))

(defn form-decode
  "Decodes a URL safe string into its original form using utf8"
  [str]
  (.decode url-encoder str "utf8"))



(defn base64-encode
  "Returns a base64 encoded string. If `url-safe?` is not-nil,
   using a URL-safe variation of the base64 algorithm"
  [str & {:keys [url-safe?]}]
  (let [bs (if (string? str) (get-bytes str) str)]
    (if url-safe?
      (Base64/encodeBase64URLSafeString bs)
      (Base64/encodeBase64String bs))))


(defn digest
  [alg in]
  (let [f
        (case (clojure.string/lower-case alg)
          "md5" md5
          "sha1" sha1
          "sha256" sha256
          "sha512" sha512
          "base64" base64-encode)]
    (f in)))

(defn base64-decode
  [str]
  (let [bs (if (string? str) (get-bytes str) str)]
    (String.
     (Base64/decodeBase64 bs))))



;; AES encryption
(defn- gen-salt
  "Generate salt with n or SALT_LEN bytes"
  [& [n]]
  (let [sr (SecureRandom.)
        bs (byte-array (or n SALT_LEN))]
    (.nextBytes sr bs)
    bs))



(defn- get-aes-factory
  [& [alg]]
  (SecretKeyFactory/getInstance "PBKDF2WithHmacSHA1"))



(defn- get-spec
  [password salt]
  (PBEKeySpec. (.toCharArray password)
               salt
               65536
               256))

(defn- aes-encrypt-intern
  [plaintext password]
  (let [salt (gen-salt)
        spec (get-spec password salt)
        factory (get-aes-factory)
        secretKey (.generateSecret factory spec)
        secret (SecretKeySpec. (.getEncoded secretKey) "AES")
        cipher (doto (Cipher/getInstance "AES/CBC/PKCS5Padding")
                 (.init Cipher/ENCRYPT_MODE secret))
        params (.getParameters cipher)
        ivspec (.getParameterSpec params IvParameterSpec)
        ivBytes (.getIV ivspec)
        encryptedTextBytes (.doFinal cipher (.getBytes plaintext "utf8"))]
    [salt ivBytes (.encodeAsString (Base64.) encryptedTextBytes)]))


(defn- aes-decrypt-intern
  [password salt ivBytes encrypted]
  (let [encryptedTextBytes (Base64/decodeBase64 encrypted)
        factory (get-aes-factory)
        spec (get-spec password salt)
        secretKey (.generateSecret factory spec)
        secret (SecretKeySpec. (.getEncoded secretKey) "AES")
        cipher (doto (Cipher/getInstance "AES/CBC/PKCS5Padding")
                 (.init Cipher/DECRYPT_MODE secret (IvParameterSpec. ivBytes)))]
    (String. (.doFinal cipher encryptedTextBytes) "utf8")))



(defn aes-encrypt-binary
  "Encrypt plaintext with the password, returns a byte-array. May throw exception on failure"
  [plaintext password]
  (let [[salt iv-bytes encrypted] (aes-encrypt-intern plaintext password)
        encrypted (.getBytes encrypted "utf8")
        ;; salt length: SALT_LEN, iv-bytes length: IVBYTE_LEN
        ;; assert the length here
        _ (assert (= 20 (count salt)))
        _ (assert (= 16 (count iv-bytes)))
        aout (byte-array (+ 36 (count encrypted)))]
    (System/arraycopy salt 0 aout 0 SALT_LEN)
    (System/arraycopy iv-bytes 0 aout SALT_LEN IVBYTE_LEN)
    (System/arraycopy encrypted 0 aout 36 (count encrypted))
    aout))



(defn aes-encrypt
    "AES encryption. Returning a base64 or hex string. Write
  to `outpath` is specified."
  [plaintext password & [{:keys [outpath as-hex]}]]
  (let [aout (aes-encrypt-binary plaintext password)
        str  (if as-hex (Hex/encodeHexString aout)  (Base64/encodeBase64URLSafeString aout))]
    (if-not outpath
      str
      (with-open [out (io/output-stream outpath)]
        (io/copy str  out)))))





(defn aes-decrypt
  "Decrypt an encrypted text encrypted with `aes-encrypt`. May throw
  exception on failure or password error. For HEX encoded input,
  set :as-hex to true."
  [encrypted password & [{:keys [as-hex]}]]
  (let [encrypted (if as-hex (Hex/decodeHex (.toCharArray encrypted)) (Base64/decodeBase64 encrypted) )
        salt (byte-array SALT_LEN)
        _ (System/arraycopy encrypted 0 salt 0 SALT_LEN)
        iv-bytes (byte-array IVBYTE_LEN)
        _ (System/arraycopy encrypted SALT_LEN iv-bytes 0 IVBYTE_LEN)
        enc-bytes (byte-array (- (count encrypted) 36))
        _ (System/arraycopy encrypted 36 enc-bytes 0 (count enc-bytes))]
    (aes-decrypt-intern password salt iv-bytes enc-bytes)))


;; Wraps AESCrypt class.

(defn encrypt-aes [plaintext password]
  "Encrypt plaintext with the password"
  (with-open [in  (ByteArrayInputStream. (.getBytes plaintext "utf-8"))
              out (ByteArrayOutputStream.)]
    (doto (AESCrypt. password)
      (.encrypt 2 in out))
    (Base64/encodeBase64URLSafeString
     (.toByteArray out))))


(defn decrypt-aes [encrypted password]
  (let [in-bytes (Base64/decodeBase64 encrypted)
        size     (count in-bytes)]
    (with-open [in  (ByteArrayInputStream. in-bytes)
                out (ByteArrayOutputStream.)]
      (doto (AESCrypt. password)
        (.decrypt size in out))
      (String.
       (.toByteArray out) "utf-8"))))
