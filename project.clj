(defproject witan.app "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [compojure "1.4.0"]
                 [ring/ring-defaults "0.1.2"]
                 [ring/ring-json "0.4.0" :exclusions [ring/ring-core]]
                 [ring/ring-jetty-adapter "1.3.2"]
                 [ring-cors "0.1.7"]
                 [liberator "0.13"]
                 [cheshire "5.5.0"]
                 [buddy/buddy-auth "0.6.2"]
                 [buddy/buddy-hashers "0.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [cc.qbits/alia "2.8.0"]
                 [cc.qbits/hayt "3.0.0-rc2"]
                 [witan.schema "0.0.1-SNAPSHOT"]
                 [prismatic/schema "1.0.1"]
                 [kixi/compojure-api "0.24.0-SNAPSHOT"]]
  :plugins [[lein-ring "0.8.13"]]
  :ring {:handler witan.app.handler/app
         :nrepl {:start? true :port 7889}}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
