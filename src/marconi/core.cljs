(ns marconi.core
  (:require-macros [cljs.core.async.macros :refer [go >! <!]])
  (:require [cljs.core.async :as async]
            [cljs.nodejs :as node]
            [cljs.reader :as reader]
            [marconi.inputs :as input]
            [marconi.outputs :as output]))

(def fs (node/require "fs"))

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

(def input-makers {'input/stdin input/stdin})
(def output-makers {'output/stdout output/stdout})

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
           inputs  (make-input-channels input)
           outputs (make-output-channels output)]
       (while true
         (let [[val chan] (alts! inputs)]
           (doseq [out outputs]
             (>! out val))))))))

(set! *main-cli-fn* start)
