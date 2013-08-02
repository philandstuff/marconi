(ns marconi.core
   (:require-macros [cljs.core.async.macros :refer [go >! <!]])
   (:require [cljs.core.async :as async]
             [cljs.nodejs :as node]))

(def dgram (node/require "dgram"))
(def redis (node/require "redis"))

(defn redis-channel []
  (let [ch (async/chan)
        client (.createClient redis)]
    (.on client "error" (fn [err]
                          (.log js/console (str "Error " err))))
    (go
     ;; TODO: close connection
     ;; TODO: react to (.on client "end)
     (while true
       (let [msg (<! ch)]
         (.lpush client "logs" msg))))
    ch))

(defn statsd-channel []
  (let [statsd-ch (async/chan)
        socket    (.createSocket dgram "udp4")]
    (go
     (while true
       (let [statsd-packet (<! statsd-ch)]
         (.send socket statsd-packet 0 (.-length statsd-packet) 8125 "127.0.0.1"))))
    statsd-ch))

(defn stdin-channel []
  (let [stdin    (.-stdin js/process)
        stdin-ch (async/chan)]
    (.resume stdin)
    (.setEncoding stdin "utf8")
    (.on stdin "data" (fn [chunk] (go (>! stdin-ch chunk))))
    stdin-ch))

(defn start [& _]
  (let [stdin-ch  (stdin-channel)
        statsd-ch (statsd-channel)
        redis-ch  (redis-channel)]
    (go
     (while true
       (let [chunk (<! stdin-ch)
             buf   (js/Buffer. (+ 4 (dec (.-length chunk))))]
         (.write buf chunk)
         (.write buf ":1|c" (dec (.-length chunk)) 4)
         (>! statsd-ch buf)
         (>! redis-ch chunk))))))

(set! *main-cli-fn* start)
