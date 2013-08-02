(defproject marconi "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[core.async "0.1.0-SNAPSHOT"]]
  :plugins [[lein-cljsbuild "0.3.2"]]
  :cljsbuild {:builds
              [{:source-paths ["src"],
                :compiler {:output-to "js/main.js",
                           :target :nodejs,
                           :output-dir "js",
                           :optimizations :simple,
                           :pretty-print true,
                           :jar true}}]})
