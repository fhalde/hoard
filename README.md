# hoard
A thread-safe generic bulk processing library in Clojure. 

- Good for usecases when there are multiple sources trying to bulk process
> Hoarding can never end, for the heart of man always covets for more


<img src="https://i.pinimg.com/originals/5a/d0/47/5ad047a18772cf0488a908d98942f9bf.gif" alt="Hoard" height="300" width="300"/></a>


## Quickstart / Usage



```clj
(require '[hoard.core :as hc])

(let [sum (atom 0)
      chunk 100
      timer 1000
      concurrency 10
      bp (hc/bulk-processor (fn [acc] (swap! sum + (count acc))) chunk timer concurrency]
    (doseq [i (range 1000)]
      (-add bp i)) ;; -add is thread-safe 
    (-close bp i)
    ;; @sum = 1000
```
__The bulk-processor reifies the following protocol__
```clj
(defprotocol IBulkProcessor
  (-close [this])
  (-flush [this])
  (-add [this request])) ;; -add is thread-safe
```
__Documentation__
```clj
(defn bulk-processor
  [f blimit btimeout bconcurrency])

;; f
;;  Single arity function to execute after blimit/btimeout with the accumulated inputs. 
;;  The parameter is a vector of inputs

;; blimit
;;  Maximum of blimit elements should be accumulated. Executes f when blimit is exceeded

;; btimeout
;;  Run f after btimeout (ms). The timer is not periodic. 
;;  The timer starts when -add adds the 1st value in the accumulating buffer. -add does not reset the timer

;; bconcurrency
;;  Numer of instances of f running at any given point of time (must be > 0)
```

## License

Copyright © 2021 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
