;; Copyright (c) 2017 Yieldbot
(ns jiggler.main
  (:require
   [clojure.string :as str]
   [clojure.tools.cli :refer [parse-opts]]
   [com.stuartsierra.component :as component]
   [jiggler.db :as db]
   [jiggler.routes :as routes])
  (:gen-class))

(defn ensure-ends-in-slash [s]
  (-> s
      (str "/")
      (str/replace #"/+$" "/")))

(def cli-options
  [["-P" "--port PORT" "Port to serve HTTP"
    :default 8666
    :parse-fn #(Integer/parseInt %)]
   ["-h" "--database-host PGHOST" "Host where PostgreSQL is running"
    :id :server-name
    :default (or (System/getenv "PGHOST") "localhost")]
   ["-p" "--database-port PGPORT" "Port where PostgreSQL is running"
    :id :port-number
    :default (or (some-> (System/getenv "PGPORT") Integer/parseInt) 5432)
    :parse-fn #(Integer/parseInt %)]
   ["-d" "--database-db PGDATABASE" "PostgreSQL database"
    :id :database-name
    :default (or (System/getenv "PGDATABASE") "jiggler")]
   ["-U" "--database-username PGUSER" "PostgreSQL username"
    :id :username
    :default (or (System/getenv "PGUSER") "jiggler")]
   ["-W" "--database-password PGPASSWORD" "PostgreSQL password; pass via PG_PASSWORD"
    :id :password
    :default (System/getenv "PGPASSWORD")]
   [nil "--base-url PATH" "The canonical URL to find this on the web"
    :default "/"
    :parse-fn ensure-ends-in-slash]
   [nil "--sqlite FILE" "Run with a SQLite database"]
   [nil "--help"]])

(defn make-system [options]
  (component/system-map
   :database  (if (:sqlite options)
                (db/make-sqlite (:sqlite options))
                (db/make-postgresql options))
   :routes    (component/using (routes/make-routes (:base-url options))
                               [:database])
   :webserver (component/using (routes/make-webserver (:port options))
                               [:routes])))

(defn exit! [code]
  (System/exit code))

(defn start [system]
  (component/start system))

(defn -main [& args]
  (let [{:keys [errors options summary]} (parse-opts args cli-options)]
    (cond
     errors          (do (run! println errors) (exit! 64))
     (:help options) (do (println summary)     (exit! 2))
     :else           (-> options
                         make-system
                         start
                         :webserver
                         routes/wait-for-webserver))))
