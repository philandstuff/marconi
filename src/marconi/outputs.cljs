(ns marconi.outputs
  (:require-macros [cljs.core.async.macros :refer [go <!]])
  (:require [cljs.core.async :as async]
            [cljs.nodejs :as node]))

(defn stdout [spec]
  (let [stdout    (.-stdout node/process)
        stdout-ch (async/chan)]
    (go (while true
          (when-let [event (<! stdout-ch)]
            (.write stdout (JSON/stringify event)))))
    stdout-ch))

(def dgram (node/require "dgram"))

(defn redis [spec]
  (let [redis    (node/require "redis")
        ch       (async/chan)
        retry-ch (async/chan)
        client   (.createClient redis 6379 "127.0.0.1"
                                (doto (js-obj)
                                  (aset "no_ready_check" true)))]
    (.on client "error" (fn [err]
                          (.log js/console (str "Error " err))))
    (go (while true
          (>! retry-ch [0 (JSON/stringify (<! ch))])))
    (go
     ;; TODO: close connection
     ;; TODO: react to (.on client "end)
     (while true
       (let [[attempt msg] (<! retry-ch)]
         (if (>= attempt 3)
           (.log js/console (str "Too many attempts, dropping" msg))
           (.lpush client "logs" msg (fn [err result]
                                       (when err
                                         (.log js/console (str "Error" err ", retrying msg" msg))
                                         (async/put! retry-ch [(inc attempt) msg]))))))))
    ch))

;; dumping ground for stuff that doesn't work atm
(defn statsd-channel []
  (let [statsd-ch (async/chan)
        socket    (.createSocket dgram "udp4")]
    (go
     (while true
       (let [statsd-packet (<! statsd-ch)]
         (.send socket statsd-packet 0 (.-length statsd-packet) 8125 "127.0.0.1"))))
    statsd-ch))
