(defproject io.factorhouse/cronut-integrant "1.2.3"

  :description "Integrant bindings for Cronut"

  :url "https://github.com/factorhouse/cronut-integrant"

  :license {:name "Apache 2.0 License"
            :url  "https://github.com/factorhosue/slipway/blob/main/LICENSE"}

  :plugins [[dev.weavejester/lein-cljfmt "0.16.3" :exclusions [org.clojure/clojure]]]

  :dependencies [[org.clojure/clojure "1.12.4"]
                 [org.clojure/tools.logging "1.3.1"]]

  :profiles {:dev     {:resource-paths ["dev-resources"]
                       :dependencies   [[integrant "1.0.1"]
                                        [ch.qos.logback/logback-classic "1.5.32"]
                                        [org.slf4j/slf4j-api "2.0.17"]
                                        [org.clojure/core.async "1.9.865"]
                                        [clj-kondo "2026.01.19" :exclusions [org.clojure/tools.reader]]]}
             :jakarta {:dependencies [[io.factorhouse/cronut "1.2.3"]]}
             :javax   {:dependencies [[io.factorhouse/cronut-javax "1.2.3"]]}
             :smoke   {:pedantic? :abort}}

  :aliases {"check"  ["with-profile" "+smoke,+jakarta" "check"]
            "kondo"  ["with-profile" "+smoke,+jakarta" "run" "-m" "clj-kondo.main" "--lint" "src:src:test:test" "--parallel"]
            "fmt"    ["with-profile" "+smoke,+jakarta" "cljfmt" "check"]
            "fmtfix" ["with-profile" "+smoke" "cljfmt" "fix"]}

  :source-paths ["src"]
  :test-paths ["test"]

  :pedantic? :warn)
