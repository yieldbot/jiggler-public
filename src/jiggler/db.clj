;; Copyright (c) 2017 Yieldbot
(ns jiggler.db
  (:require
   [clojure.java.io :as jio]
   [clojure.java.jdbc :as jdbc]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [hikari-cp.core :as hcp]))

(defn link-query [shortlink]
  ["SELECT * FROM links WHERE shortlink = ?" shortlink])

(defn get-link [{:keys [pool]} shortlink]
  (if shortlink
    (jdbc/with-db-connection [conn {:datasource pool}]
      (->> (jdbc/query conn (link-query shortlink))
           first
           (merge {:shortlink shortlink})))))

(defn insert-history-query [link]
  (let [q (format "INSERT INTO history (%s) VALUES (%s)"
                  (str/join ", " (map name (keys link)))
                  (str/join ", " (for [_ link] "?")))]
    (cons q (vals link))))

(defn insert-link-query [link]
  (let [q (format "INSERT INTO links (%s) VALUES (%s)"
                  ;; insert columns
                  (str/join ", " (map name (keys link)))
                  ;; insert values
                  (str/join ", " (for [_ link] "?")))]
    (concat [q]
            ;; Insert values
            (vals link))))

(defn update-link-query [link]
  (let [q (format "UPDATE links SET %s WHERE shortlink=?"
                  (str/join ", " (for [k (keys link)]
                                   (str (name k) "=?"))))]
    (concat [q]
            ;; Update values
            (vals link)
            [(:shortlink link)])))

(defn update-link [{:keys [pool]} shortlink target updated_by]
  (let [link {:shortlink shortlink
              :target target
              :updated_by updated_by}]
    (jdbc/with-db-connection [conn {:datasource pool}]
      (jdbc/with-db-transaction [transaction conn]
        ;; Record the history, just in case we can use it someday.
        (jdbc/execute! transaction (insert-history-query link))
        (jdbc/execute! transaction
                       (if-let [existing-link (->> shortlink
                                                   link-query
                                                   (jdbc/query transaction)
                                                   first)]
                         (update-link-query link)
                         (insert-link-query link)))
        (-> (jdbc/query transaction (link-query shortlink))
            first
            (assoc :modified? true))))))

(defn increment-usage-query [shortlink]
  ["UPDATE links SET usage = usage + 1 WHERE shortlink=?" shortlink])

(defn increment-usage! [{:keys [pool]} shortlink]
  (jdbc/with-db-connection [conn {:datasource pool}]
    (jdbc/execute! conn (increment-usage-query shortlink))))

(defn search-query [text limit]
  (if (or (nil? text) (empty? text))
    ["SELECT * FROM links ORDER BY usage DESC LIMIT ?" limit]
    ["SELECT * FROM links WHERE shortlink LIKE ? OR target LIKE ? ORDER BY usage DESC LIMIT ?"
     (str \% text \%)
     (str \% text \%)
     limit]))

(defn search [{:keys [pool]} text limit]
  (jdbc/with-db-connection [conn {:datasource pool}]
    (jdbc/query conn (search-query text limit))))

(defn init-db!
  "Initialize the database."
  [pool]
  ;; Initialize the database
  (jdbc/with-db-connection [conn {:datasource pool}]
    (as-> "sql/001-init.up.sql" $
          (jio/resource $)
          (slurp $)
          (str/split $ #";")
          (map #(str % ";") $)
          ;; Drop the last element
          (butlast $)
          (jdbc/db-do-commands conn $))))

(defmacro named [& syms]
  `(sorted-map ~@(interleave (map keyword syms) syms)))

(defrecord SQLite [dbfile pool]
  component/Lifecycle
  (start [component]
    (assoc component :pool
           (let [db-pool (hcp/make-datasource
                          {:driver-class-name "org.sqlite.JDBC"
                           :jdbc-url (str "jdbc:sqlite:" dbfile)})]
             ;; Set up the database--it might be new.
             (init-db! db-pool)
             (log/infof "Connected to SQLite database: %s" dbfile)
             db-pool)))
  (stop [component]
    (some-> pool .close)
    (dissoc component :pool)))

(defn make-sqlite [dbfile]
  (map->SQLite {:dbfile dbfile}))

(defrecord PostgreSQL [server-name port-number database-name username password
                       pool]
  component/Lifecycle
  (start [component]
    (assoc component :pool
           (let [db-pool (hcp/make-datasource
                          (assoc (named server-name port-number username
                                        password database-name)
                                 :adapter "postgresql"))]
             ;; Don't try to initialize the DB with PostgreSQL--we probably
             ;; don't have permissions.
             (log/infof "Connected to database: %s"
                        (named server-name port-number database-name username))
             db-pool)))
  (stop [component]
    (some-> pool .close)
    (dissoc component :pool)))

(defn make-postgresql [m]
  (-> m
      (select-keys [:server-name :port-number :database-name :username
                    :password])
      map->PostgreSQL))
