(defproject witan.app "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.3.1"]
                 [ring/ring-defaults "0.1.2"]
                 [liberator "0.13"]
                 [org.clojure/tools.logging "0.3.1"]
                 [cheshire "5.5.0"]]
  :plugins [[lein-ring "0.8.13"]]
  :ring {:handler witan.app.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
