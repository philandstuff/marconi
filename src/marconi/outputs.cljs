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
