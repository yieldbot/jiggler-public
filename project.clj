(defproject jiggler-public "0.1.0"
  :description "Yieldbot shortlinks"
  :url "https://github.com/yieldbot/jiggler-public"
  :license {:name "All Rights Reserved"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 ;; For command-line arguments
                 [org.clojure/tools.cli "0.3.5"]
                 ;; For serving
                 [aleph "0.4.2-alpha8"]
                 ;; HTTP logging
                 [ring-logger "0.7.6"]
                 [org.apache.logging.log4j/log4j-api "2.7"]
                 [org.apache.logging.log4j/log4j-core "2.7"]
                 [org.apache.logging.log4j/log4j-slf4j-impl "2.7"]
                 ;; For routing
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.2.1"]
                 ;; For SQL connection pooling
                 [hikari-cp "1.7.0"]
                 ;; For PostgreSQL
                 [org.clojure/java.jdbc "0.6.1"]
                 [org.postgresql/postgresql "9.4.1211"]
                 ;; For SQLite
                 [org.xerial/sqlite-jdbc "3.14.2.1"]
                 ;; For HTML templating
                 [enlive "1.1.6"]
                 ;; For URI templating
                 [org.apache.httpcomponents/httpclient "4.5.3"]
                 ;; For managing state
                 [com.stuartsierra/component "0.3.1"]]
  :main jiggler.main
  :aot [jiggler.main]
  :test-selectors {:default (complement (some-fn :integration))
                   :integration :integration
                   :all (constantly true)}
  :profiles {:dev {:aot ^:replace []
                   :dependencies [[ring/ring-mock "0.3.0"]]}})
