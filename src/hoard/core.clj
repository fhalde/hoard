(ns hoard.core
  (:require [clojure.core.async :refer [<!! chan >!! go-loop timeout alts! >! thread close!]]))


(defprotocol IBulkProcessor
  (-close [this])
  (-flush [this])
  (-add [this request]))


(defmacro exec-with-limiter
  [limiter & body]
  `(do
     (>! ~limiter :exec)
     (thread
       (try
         ~@body
         (finally
           (<!! ~limiter))))))


(defn bulk-processor
  [f blimit btimeout bconcurrency]
  (let [requests-ch (chan)
        controller-ch (chan)
        rate-limiter (chan bconcurrency)]
    (go-loop [acc [] inflight 0 timer nil]
      (let [chs (concat [requests-ch controller-ch]
                        (when timer
                          [timer]))
            [v _] (alts! chs)]
        (if v
          (case v
            :close (when (seq acc)
                     (exec-with-limiter rate-limiter (f acc)))
            :flush (do
                     (when (seq acc)
                       (exec-with-limiter rate-limiter (f acc)))
                     (recur [] 0 nil))
            (let [acc (conj acc v)]
              (if (<= blimit (+ 1 inflight))
                (do (exec-with-limiter rate-limiter (f acc))
                    (recur [] 0 nil))
                (recur acc (inc inflight) (or timer (timeout btimeout))))))
          ;; timeout
          (when (seq acc)
            (exec-with-limiter rate-limiter (f acc))
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
