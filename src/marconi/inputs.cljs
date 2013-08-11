(ns marconi.inputs
  (:require [cljs.core.async :as async]
            [cljs.nodejs :as node]
            [cljs.reader :as reader]))

(defn- wrap-json [line]
  (clj->js {"@message" line}))

(defn stdin [spec]
  (let [stdin    (.-stdin node/process)
        format   (:format spec)
        format-fns {:json JSON/parse, :text wrap-json}
        format-fn (format format-fns)
        stdin-ch (async/chan)
        carrier  (node/require "carrier")]
    (.resume stdin)
    (.setEncoding stdin "utf8")
    (.carry carrier stdin (fn [line]
                            (let [output (format-fn line)]
                              (async/put! stdin-ch output))))
    stdin-ch))
