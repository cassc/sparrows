(ns sparrows.pool
  (:require
   [taoensso.timbre :as t]
   [com.climate.claypoole :as cp])
  (:import
   java.util.concurrent.ExecutorService))


(defonce ^{:private true} sparrows-pool (atom {}))

(defn- register-lazy-pool
  "Register a pool for later use.
  id should be a unique keyword
  pool should be a delay of (cp/pool ...) instance"
  [id pool]
  {:pre [id pool (keyword? id) (delay? pool)]}
  (do
    (swap! sparrows-pool assoc id pool)
    pool))

;; Usage::
;; register pool(s) 
;; (register-lazy-pool :email-sender-pool (delay (cp/threadpool 1 :daemon true :name "email-sender-pool")))
;;
;; Get pool by id
;; (id->pool :email-sender-pool)
;;
;; destroy when server shutdown
;;  (register-shutdownhook
;; (fn shutdown-handler []
;;   (timbre/info "Application is shutting down. Cleaning ...")
;;   (try
;;     (destroy-lazy-pools)
;;     (catch Throwable e
;;       (timbre/error e "Error caught when shutting down ..."))
;;     (finally
;;       (timbre/info "Cleaning success!")))))

;;(register-lazy-pool :orderquery-thread-pool (delay (cp/threadpool 4 :daemon false :name "orderquery-thread-poool")))

(defn destroy-lazy-pools
  []
  (t/info "Shutting down all thread pools ... ")
  (doseq [p (seq (vals @sparrows-pool))]
    (when (and p (realized? p))
      (.shutdown ^ExecutorService @p))))

(defn id->pool
  [id]
  (when-let [p (id @sparrows-pool)]
    @p))

(defn- register-lazy-pool
  "Register a pool for later use.
  id should be a unique keyword
  pool should be a delay (cp/pool ...) instance"
  [id pool]
  {:pre [id pool (keyword? id) (delay? pool)]}
  (do
    (swap! sparrows-pool assoc id pool)
    pool))
