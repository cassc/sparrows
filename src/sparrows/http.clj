(ns sparrows.http
  (:require
   [clojure.java.io :as io]
   [clojure.string :as s]
   [org.httpkit.client :as hc]
   [clj-http.client :as client]
   [taoensso.timbre :as timbre])
  (:import
   org.apache.http.conn.ssl.SSLContexts
   [java.security KeyStore]
   [org.apache.commons.mail  HtmlEmail MultiPartEmail EmailAttachment]))


(timbre/refer-timbre)

(defonce cs (clj-http.cookies/cookie-store))
(defonce default-ua "Mozilla/5.0 (X11; Linux i686; rv:30.0) Gecko/20100101 Firefox/30.0 Iceweasel/30.0")

(defonce default-request-map
  {:accept "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
   :accept-language "en,zh-cn;q=0.7,en-us;q=0.3"
   :user-agent default-ua
   :cookie-store cs
   :socket-timeout 10000
   :conn-timeout 5000
   ;; io exception handler
   :retry-handler (fn [ex try-count http-context]
                    (println "Got:" ex)
                    (if (> try-count 2) false true))
   :decode-body-headers true :as :auto})

(defn get-status-by-exception
  "Get http status code from an exception of client/request."
  {:deprecated "0.1.8"}
  [e]
  (try
    (:status (:object (.getData e)))
    (catch Exception f)))

(defmacro def-httpmethod
  [method]
  `(defn ~method
     ~(str "Issues an client/" method " request which is wrapped in a try-catch block.")
     [~'url & [~'request-map]]
     (let [request# ~(symbol (str "client/" (clojure.string/lower-case method)))]
       (request# ~'url (merge default-request-map ~'request-map)))))


(def-httpmethod GET)
(def-httpmethod POST)
(def-httpmethod PUT)
(def-httpmethod DELETE)
(def-httpmethod PATCH)
(def-httpmethod OPTIONS)
(def-httpmethod HEAD)

;; Available options for self-signed https connection:
;;  - use {:insecure true}
;;  - or pass keystore path, eg.
;; (client/get "https://example.com" {:keystore "/path/to/keystore.ks"
;;                                    :keystore-type "jks" ; default: jks
;;                                    :keystore-pass "secretpass"
;;                                    :trust-store "/path/to/trust-store.ks"
;;                                    :trust-store-type "jks" ; default jks
;;                                    :trust-store-pass "trustpass"})


(def default-client-options
  {:timeout 5000
   :follow-redirects true
   :headers {"accept" "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8" ;;"application/json; charset=utf-8"
             "accept-language" "en-US,en;q=0.8,zh;q=0.6"
             "accept-encoding" "gzip"
             "user-agent" default-ua}})


(defn async-request
  "Send an async http-kit request.

  When request-map is not provided, 

  - the provided options, i.e.,`params, body, query-params, form-params` 
  will be merged into the default-client-options and used as the request map.
  - the provided `headers` will be merged into the default headers in `default-client-options`"
  [{:keys [method url params body query-params form-params request-map content-type insecure? sslengine multipart headers]}]
  {:pre [method url]}
  (let [assoc-when (fn [m k v] (conj m (when v [k v])))
        m (case method
            :get hc/get
            :post hc/post
            :patch hc/patch
            :put hc/put
            :delete hc/delete
            :head hc/head
            :options hc/options)]
    (m
     url
     (or request-map
         (-> default-client-options
             (assoc :insecure? insecure?)
             (assoc-when :headers (merge (default-client-options :headers) headers))
             (assoc-when :sslengine sslengine)
             (assoc-when :body body)
             (assoc-when :multipart multipart)
             (assoc-when :form-params form-params)
             (assoc-when :query-params query-params))))))

(defn send-email
  "Send email. Note that target is a seq of email
  addresses. `attachment` is an instance of EmailAttachment. Refer to
  http://commons.apache.org/proper/commons-email/userguide.html for
  examples. Note that to is a collection of recipients

  For self-signed mail servers, set the value to `false` for `:ssl?`."
  [{:keys [from to subject html text attachment smtp-host smtp-port smtp-user smtp-pass ssl?]
    :or   {ssl? true}}]
  (let [base (HtmlEmail.)
        base (doto base
               (.setHostName smtp-host)
               (.setSmtpPort smtp-port)
               (.setSSL (if ssl? true false))
               (.setFrom from)
               (.setSubject subject)
               (.setAuthentication smtp-user smtp-pass)
               (.setCharset "utf-8"))]
    (when attachment
      (.attach ^HtmlEmail base ^EmailAttachment attachment))
    (when html
      (.setHtmlMsg base html))
    (when text
      (.setTextMsg base text))
    (doseq [person to]
      (.addTo ^HtmlEmail base ^String person))
    (.send base)))


;; (async-request
;;  {:method :post
;;   :sslengine (make-sslengine
;;               {:pass "password"
;;                :cert (io/file "cert.file")
;;                :keystore-type "PKCS12"})
;;   :url url
;;   :body body}) 
(defn make-sslengine
  "
  For certificate type and info, see https://api.mch.weixin.qq.com/mmpaymkttransfers/promotion/transfers
  See also `make-ssl-context` for server ssl context"
  [{:keys [^String pass ^File cert ^String keystore-type]}]
  (let [ks         (KeyStore/getInstance (or keystore-type "PKCS12"))
        passphrase (.toCharArray pass)
        _          (.load ks (io/input-stream cert) passphrase)
        ssl-ng     (.. (SSLContexts/custom)
                       (loadKeyMaterial ks passphrase)
                       build
                       (createSSLEngine "client" 80))]
    (doto ssl-ng
      (.setUseClientMode true))))

