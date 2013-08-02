(defproject marconi "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[core.async "0.1.0-SNAPSHOT"]
                 [org.bodil/cljs-noderepl "0.1.10"]]
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
  :plugins [[lein-cljsbuild "0.3.2"]
            [org.bodil/lein-noderepl "0.1.10"]]
  :cljsbuild {:builds
              [{:source-paths ["src"],
                :compiler {:output-to "js/main.js",
                           :target :nodejs,
                           :output-dir "js",
                           :optimizations :simple,
                           :pretty-print true,
                           :jar true}}]})
