(ns marconi.core
   (:require-macros [cljs.core.async.macros :refer [go >! <!]])
   (:require [cljs.core.async :as async]
             [cljs.nodejs :as node]
             [cljs.reader :as reader]))

(def dgram (node/require "dgram"))
(def redis (node/require "redis"))
(def fs (node/require "fs"))

(defn redis-channel []
  (let [ch       (async/chan)
        retry-ch (async/chan)
        client   (.createClient redis 6379 "127.0.0.1"
                                (doto (js-obj)
                                  (aset "no_ready_check" true)))]
    (.on client "error" (fn [err]
                          (.log js/console (str "Error " err))))
    (go (while true
          (>! retry-ch [0 (<! ch)])))
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
    (.on stdin "data" (fn [chunk] (async/put! stdin-ch chunk)))
    stdin-ch))

(defn read-config [config-filename]
  (let [ch (async/chan)]
    (.readFile fs config-filename "utf8"
               (fn [error data]
                 (if error
                   (.log js/console (str "Error" error))
                   (let [r (reader/push-back-reader data)]
                     (go
                      (loop [d (reader/read r)]
                        (when d
                          (>! ch d)
                          (recur (reader/read r)))))))))
    ch))

(defn start [config-filename]
  (let [stdin-ch  (stdin-channel)
        statsd-ch (statsd-channel)
        redis-ch  (redis-channel)
        config-ch (read-config config-filename)]
    (go
     (while true
       (let [piece (<! config-ch)]
         (println "foo")
         (prn piece))))
    #_(go
       (while true
         (let [chunk (<! stdin-ch)
               buf   (js/Buffer. (+ 4 (dec (.-length chunk))))]
         (.write buf chunk)
         (.write buf ":1|c" (dec (.-length chunk)) 4)
         (>! statsd-ch buf)
         (>! redis-ch chunk))))))

(set! *main-cli-fn* start)
