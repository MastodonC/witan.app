(def slf4j-version "1.7.12")
(def cider-nrepl-version "0.9.1")

(defproject witan.app "0.1.3-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.apache.httpcomponents/httpcore "4.4.3"]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.cli     "0.3.1"]
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
                 [cc.qbits/alia "2.10.0"]
                 [cc.qbits/hayt "3.0.0-rc2"]
                 [prismatic/schema "1.0.3"]
                 [kixi/schema-contrib "0.2.0"]
                 [kixi/compojure-api "0.24.0"]
                 [org.clojure/tools.logging      "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.3"]
                 [org.slf4j/jul-to-slf4j         ~slf4j-version]
                 [org.slf4j/jcl-over-slf4j       ~slf4j-version]
                 [org.slf4j/log4j-over-slf4j     ~slf4j-version]
                 [javax.mail/mail                "1.4.7"]
                 [overtone/at-at "1.2.0"]
                 [com.amazonaws/aws-java-sdk "1.10.27"]
                 [amazonica "0.3.35" :exclusions [com.amazonaws/aws-java-sdk]]
                 [clojure-csv/clojure-csv "2.0.1"]
                 [witan.models "0.1.5"]
                 [clj-http "2.0.0"]
                 [slugger "1.0.1"]
                 [com.stuartsierra/component "0.3.0"]
                 [markdown-clj "0.9.82"]

                 ;; do this here to avoid clashes
                 ;; with local profiles.clj. We need
                 ;; to choose a consistent version so
                 ;; we can remote repl with completion.
                 [cider/cider-nrepl              ~cider-nrepl-version]
                 [org.clojure/tools.namespace    "0.2.10"]
                 [org.clojure/tools.nrepl        "0.2.12"]
                 [ring-logger "0.7.5"]
                 [environ "1.0.1"]]

  :plugins [[lein-ring "0.8.13"]
            [s3-wagon-private "1.1.2"]]

  :java-source-paths ["java-src"]
  :javac-options ["-target" "1.7" "-source" "1.7" "-Xlint:-options"]

  :jvm-opts ["-Xmx2048m"]

  :repl-options {:init-ns user}

  :uberjar-name "witan-app.jar"

  :main ^:skip-aot witan.Bootstrap

  :profiles
  {:dev {:source-paths ["dev"]
         :dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}}

  ;; You need to arrange for the environment variables:
  ;;   MC_AWS_USERNAME   to be your AWS access key
  ;;   MC_AWS_PASSPHRASE to be your AWS secret key
  ;; there is a sample.lein-credentials file which you can fill in and
  ;; source from your shell
  :repositories [["releases" {:url "s3p://mc-maven-repo/releases"
                              :username :env/mc_aws_username
                              :passphrase :env/mc_aws_passphrase}]
                 ["snapshots" {:url "s3p://mc-maven-repo/snapshots"
                               :username :env/mc_aws_username
                               :passphrase :env/mc_aws_passphrase}]]
  :release-tasks [["change" "version"
                   "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "release-v"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
