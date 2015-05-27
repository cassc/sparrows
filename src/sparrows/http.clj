(ns sparrows.http
  (:require
   [clj-http.client :as client]
   [taoensso.timbre :as timbre])
  (:import
   [org.apache.commons.mail  HtmlEmail MultiPartEmail ]))


(timbre/refer-timbre)

(defonce cs (clj-http.cookies/cookie-store))

(defonce default-request-map
  {:accept "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
   :accept-language "en,zh-cn;q=0.7,en-us;q=0.3"
   :user-agent "Mozilla/5.0 (X11; Linux i686; rv:30.0) Gecko/20100101 Firefox/30.0 Iceweasel/30.0"
   :cookie-store cs
   :socket-timeout 10000
   :conn-timeout 5000
   :retry-handler (fn [ex try-count http-context]
                    (println "Got:" ex)
                    (if (> try-count 2) false true))
   :decode-body-headers true :as :auto})

(defn get-status-by-exception
  "Get http status code from an exception of client/request."
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


;; Available options for self-signed https connection:
;;  - use {:insecure true}
;;  - or pass keystore path, eg.
;; (client/get "https://example.com" {:keystore "/path/to/keystore.ks"
;;                                    :keystore-type "jks" ; default: jks
;;                                    :keystore-pass "secretpass"
;;                                    :trust-store "/path/to/trust-store.ks"
;;                                    :trust-store-type "jks" ; default jks
;;                                    :trust-store-pass "trustpass"})



(defn send-email
  "Send email. Note that target is a seq of email
  addresses. `attachment` is an instance of EmailAttachment. Refer to
  http://commons.apache.org/proper/commons-email/userguide.html for
  examples. Note that to is a collection of recipients"
  [{:keys [from to subject html text attachment smtp-host smtp-port smtp-user smtp-pass]}]
  (let [base (if attachment (MultiPartEmail.) (HtmlEmail.) )
        base (doto base
                 (.setHostName smtp-host)
               (.setSmtpPort smtp-port)
               (.setSSL true)
               (.setFrom from)
               (.setSubject subject)
               (.setAuthentication smtp-user smtp-pass)
               (.setCharset "utf-8"))]
    (when attachment
      (.attach base attachment))
    (when html
      (.setHtmlMsg base html))
    (when text
      (.setTextMsg base text))
    (doseq [person to]
      (.addTo base person))
    (.send base)))
