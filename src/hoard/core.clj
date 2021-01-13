(ns hoard.core
  (:require [clojure.core.async :refer [<!! chan >!! go-loop timeout alts! thread close!]]))


(defprotocol IBulkProcessorListener
  (-before [this requests])
  (-success [this requests response])
  (-failure [this requests exception]))


(defprotocol IBulkProcessor
  (-close [this])
  (-flush [this])
  (-add [this request]))


(defn exec-with-limiter
  [f listener rate-limiter requests]
  (thread
    (>!! rate-limiter :exec)
    (try
      (-before listener requests)
      (-success listener requests (f requests))
      (catch Exception e
        (-failure listener requests e))
      (finally
        (<!! rate-limiter)))))


(defn bulk-processor
  [f blistener blimit btimeout bconcurrency]
  (let [requests-ch (chan)
        controller-ch (chan)
        rate-limiter (chan bconcurrency)
        exec (partial exec-with-limiter f blistener rate-limiter)]
    (go-loop [acc [] inflight 0 timer nil]
      (let [chs (concat [requests-ch controller-ch]
                        (when timer
                          [timer]))
            [v _] (alts! chs)]
        (if v
          (case v
            :close (when (seq acc)
                     (exec acc))
            :flush (do
                     (when (seq acc)
                       (exec acc))
                     (recur [] 0 nil))
            (let [acc (conj acc v)]
              (if (<= blimit (+ 1 inflight))
                (do (exec acc)
                    (recur [] 0 nil))
                (recur acc (inc inflight) (or timer (timeout btimeout))))))
          ;; timeout
          (when (seq acc)
            (exec acc)
            (recur [] 0 nil)))))
    (reify IBulkProcessor
      (-close [_]
        (close! requests-ch)
        (>!! controller-ch :close)
        (close! controller-ch))
      (-flush [_]
        (>!! controller-ch :flush))
      (-add [_ req]
        (>!! requests-ch req)))))
