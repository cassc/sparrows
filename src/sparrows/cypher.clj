(ns sparrows.cypher
  "The following hash functions are provided by this namespace:
    - md5
    - sha1
    - sha256
    - sha512
    - 'www-form-urlencoded' encoding scheme: form-encode and form-decode
    - base64
    - aes (requires unlimited JCE extension for JVM, see http://stackoverflow.com/questions/6481627/java-security-illegal-key-size-or-default-parameters/6481658#6481658"
  (:require [clojure.java.io :as io]
            [clojure.string :as s])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream File InputStream]
           [clojure.lang Reflector]
           java.security.SecureRandom
           [java.util.zip GZIPInputStream GZIPOutputStream]
           [javax.crypto Cipher SecretKeyFactory]
           [javax.crypto.spec IvParameterSpec PBEKeySpec SecretKeySpec]
           [org.apache.commons.codec.binary Base64 Hex]
           org.apache.commons.codec.digest.DigestUtils
           org.apache.commons.codec.net.URLCodec
           third.AESCrypt))

(set! *warn-on-reflection* nil)

; salt length
(def SALT_LEN 20)
; ivbytes length
(def IVBYTE_LEN 16)

(def ^{:tag URLCodec} url-encoder
  (URLCodec. "utf8"))

(defn rand-str
  "Generate a random alpha-numeric string with length n"
  [& [n]]
  (let [pw-chars "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        rand-chars (repeatedly (or n 6) #(rand-nth pw-chars))]
    (apply str rand-chars)))


(defn get-bytes
  "Get bytes array using utf8"
  ^bytes [^String str]
  (.getBytes str "utf8"))

(defn digest-with
  "Digest with the specified algorithm. If `as-bytes?` is true, returns a byte array intead of string.
  Supported algorithms: md2, md5, sha1, sha256, sha512"
  [alg in & {:keys [as-bytes?]}]
  (let [alg (s/lower-case (name alg))
        m (if as-bytes? alg (str alg "Hex"))
        file? (isa? (class in) File)
        in (if file? (io/input-stream in) in)
        stream? (or file? (isa? (class in) InputStream))]
    (if stream?
      (with-open [in in]
        (Reflector/invokeStaticMethod DigestUtils m (into-array [in])))
      (Reflector/invokeStaticMethod DigestUtils m (into-array [in])))))

(defn md2 [& args] (apply digest-with :md2 args))
(defn md5 [& args] (apply digest-with :md5 args))
(defn sha1 [& args] (apply digest-with :sha1 args))
(defn sha256 [& args] (apply digest-with :sha256 args))
(defn sha512 [& args] (apply digest-with :sha512 args))

(defn url-encode
  "URL encode input byte array as a utf8-encoded string, or if as-bytes? is true, as
  byte-array"
  [^bytes bs & {:keys [as-bytes?]}]
  (if as-bytes?
    (.encode url-encoder bs)
    (String. ^bytes (url-encode bs :as-bytes? 't) "utf8")))


(defn url-decode
  "URL decode input byte array as a string, or if as-bytes? is true, as
  byte-array"
  [^bytes bs & {:keys [as-bytes?]}]
  {:pre [(= (Class/forName "[B") (class bs))]}
  (if as-bytes?
    (.decode url-encoder bs)
    (String. ^bytes (url-decode bs :as-bytes? 't) "utf8")))



;;; URL-encode/decode
(defn form-encode
  "URL Encode a string as bytes(:as-bytes?) or utf8 encoded string"
  [^String s & options]
  (apply url-encode (.getBytes s "utf8") options))

(defn form-decode
  "URL decode a string as bytes(:as-bytes?) or utf8 encoded string"
  [^String s & options]
  (apply url-decode (.getBytes s "utf8") options))


;;; BASE64 encode/decode
(defn base64-encode-bytes
  "Returns a base64 encoded string. If `url-safe?` is not-nil,
   using a URL-safe variation of the base64 algorithm"
  [bs & {:keys [url-safe? as-bytes?]
         :or   {url-safe? true}}]
  (if url-safe?
    (if as-bytes?
      (Base64/encodeBase64URLSafe bs)
      (Base64/encodeBase64URLSafeString bs))
    (if as-bytes?
      (Base64/encodeBase64 bs)
      (Base64/encodeBase64String bs))))

(defn base64-decode-bs
  "Decode a base64 encoded string or bytes"
  [^bytes bs & {:keys [as-bytes?]}]
  (if as-bytes?
    (Base64/decodeBase64 bs)
    (String. (Base64/decodeBase64 bs))))

(defprotocol Base64Codec
  "Defines form-encode and form-decode methods for url-safe encode and
  decode string/bytes/input streams. "
  (-base64-encode [this options])
  (-base64-decode [this options]))

(extend (Class/forName "[B")
  Base64Codec
  {:-base64-encode (fn [s options] (apply base64-encode-bytes s options))
   :-base64-decode (fn [s options] (apply base64-decode-bs s options))})

(extend String
  Base64Codec
  {:-base64-encode (fn [^String s options] (apply base64-encode-bytes (.getBytes s "utf8") options))
   :-base64-decode (fn [s options] (apply base64-decode-bs (.getBytes s "utf8") options))})

(extend InputStream
  Base64Codec
  {:-base64-encode (fn [in options] (with-open [in in]
                                      (-base64-encode (slurp (io/reader in)) options)))
   :-base64-decode (fn [in options] (with-open [in in]
                                      (-base64-decode (slurp (io/reader in)) options)))})

(extend File
  Base64Codec
  {:-base64-encode (fn [in options] (-base64-encode (io/input-stream in) options))
   :-base64-decode (fn [in options] (-base64-decode (io/input-stream in) options))})

(extend nil
  Base64Codec
  {:-base64-encode (fn [s options] nil)
   :-base64-decode (fn [s options] nil)})

(defn base64-encode
  "Available options: :url-safe? :as-bytes?"
  [x & options]
  (-base64-encode x options))

(defn base64-decode
  "Available options:as-bytes? "
  [x & options]
  (-base64-decode x options))


;;; Compress and decompress strings
(defn compress-string
  "Compress a string using gzip, return the base64 encoded bytes"
  [^String in]
  (let [aos (ByteArrayOutputStream.)]
    (with-open [out (GZIPOutputStream. aos)]
      (.write out (get-bytes in)))
    (let [bs (.toByteArray aos)]
      (base64-encode bs ))))

(defn decompress-string
  "Decompress a string compressed by compress-string"
  [^String in]
  (let [input-bytes (base64-decode in :as-bytes? true)
        ais         (ByteArrayInputStream. input-bytes)]
    (with-open [in (GZIPInputStream. ais)]
      (slurp (io/reader in)))))

(def base64
  "Base64 encode a string or byte array. Available options: :url-safe? :as-bytes?"
  base64-encode)

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
  [^String password salt]
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
  [^String plaintext ^String password]
  (let [[salt iv-bytes ^String encrypted] (aes-encrypt-intern plaintext password)
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
  "DEPRECATED: Use 'encrypt-aes' instead.
  AES encryption. Returning a base64 or hex string. Write
  to `outpath` is specified."
  {:deprecated "0.1.3"}
  [plaintext password & [{:keys [outpath as-hex]}]]
  (let [aout (aes-encrypt-binary plaintext password)
        str  (if as-hex (Hex/encodeHexString aout)  (Base64/encodeBase64URLSafeString aout))]
    (if-not outpath
      str
      (with-open [out (io/output-stream outpath)]
        (io/copy str  out)))))





(defn aes-decrypt
  "DEPRECATED: Use 'decrypt-aes' instead.
  Decrypt an encrypted text encrypted with `aes-encrypt`. May throw
  exception on failure or password error. For HEX encoded input,
  set :as-hex to true."
  {:deprecated "0.1.3"}
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

(defn encrypt-aes
  "Encrypt plaintext with the password"
  [^String plaintext ^String password]
  (with-open [in  (ByteArrayInputStream. (.getBytes plaintext "utf-8"))
              out (ByteArrayOutputStream.)]
    (doto (AESCrypt. password)
      (.encrypt 2 in out))
    (Base64/encodeBase64URLSafeString
     (.toByteArray out))))


(defn decrypt-aes
  "Decrypt an `encrypted` string or byte-array using the provided `password`"
  [encrypted ^String password]
  (let [in-bytes (Base64/decodeBase64 encrypted)
        size     (count in-bytes)]
    (with-open [in  (ByteArrayInputStream. in-bytes)
                out (ByteArrayOutputStream.)]
      (doto (AESCrypt. password)
        (.decrypt size in out))
      (String.
       (.toByteArray out) "utf-8"))))

(defn- ->bytes
  [b]
  (cond 
    (= (class b) String) (get-bytes b)
    (= (class b) (Class/forName "[B")) b))

(defn slow=
  "Slow equals compare. Takes string or byte array as arugments.

  see https://crackstation.net/hashing-security.htm#javasourcecode"
  [sa sb]
  (let [sa (->bytes sa)
        sb (->bytes sb)]
    (zero?
     (reduce #(bit-or % %2)
             (bit-xor (count sa) (count sb))
             (map #(bit-xor %1 %2) sa sb)))))
