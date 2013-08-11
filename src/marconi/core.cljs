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

(defn wrap-json [line]
  (JSON/stringify (clj->js {"@message" line})))

(defn stdin-channel [spec]
  (let [stdin    (.-stdin js/process)
        format   (:format spec)
        format-fns {:json identity, :text wrap-json}
        format-fn (format format-fns)
        stdin-ch (async/chan)]
    (.resume stdin)
    (.setEncoding stdin "utf8")
    (.on stdin "data" (fn [chunk]
                        ;; FIXME assumes each chunk is one line
                        ;; use carrier from npm to fix this
                        (let [output (format-fn chunk)]
                          (async/put! stdin-ch output))))
    stdin-ch))

(defn stdout-channel [spec]
  (let [stdout    (.-stdout js/process)
        stdout-ch (async/chan)]
    (go (while true
          (when-let [event (<! stdout-ch)]
            (.write stdout event))))
    stdout-ch))

(defn read-config [config-filename]
  (let [ch (async/chan)]
    (.readFile fs config-filename "utf8"
               (fn [error data]
                 (if error
                   (.log js/console (str "Error" error))
                   (let [r (reader/push-back-reader data)]
                     (go
                      (loop [result []]
                        (if-let [d (reader/read r)]
                          (recur (conj result d))
                          (do
                            (>! ch result)
                            (async/close! ch)))))))))
    ch))

(def input-makers {'input/stdin stdin-channel})
(def output-makers {'output/stdout stdout-channel})

(defn make-input-channel [spec]
  (let [type  (:type spec)
        maker (type input-makers)]
    (maker spec)))

(defn make-input-channels [specs]
  (map make-input-channel specs))

(defn make-output-channel [spec]
  (let [type  (:type spec)
        maker (type output-makers)]
    (maker spec)))

(defn make-output-channels [specs]
  (map make-output-channel specs))

(defn start [config-filename]
  (let [config-ch  (read-config config-filename)]
    (go
     (let [config   (<! config-ch)
           {:keys [input output]} (group-by (comp keyword namespace :type) config)
           inputs (make-input-channels input)
           outputs (make-output-channels output)
           ]
       (while true
         (let [[val chan] (alts! inputs)]
           (doseq [out outputs]
             (>! out val))))))))

(set! *main-cli-fn* start)
