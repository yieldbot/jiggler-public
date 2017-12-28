;; Copyright (c) 2017 Yieldbot
(ns jiggler.db-test
  (:require
   [clojure.java.io :as jio]
   [clojure.test :refer :all]
   [com.stuartsierra.component :as component]
   [jiggler.db :as db]))

(defn mk-tempfile []
  (.getCanonicalPath
   (doto (java.io.File/createTempFile "jiggler-" ".db.tmp")
     .deleteOnExit)))

(defmacro with-tempfile [[f] & more]
  `(let [~f (mk-tempfile)]
     ~@more))

(deftest test-sqlite
  (with-tempfile [f]
    (let [db (-> f db/make-sqlite component/start)]
      (is (= {:shortlink "a"} (db/get-link db "a")))
      (is (= {:shortlink "a" :target "b" :updated_by "leon" :usage 0 :modified? true}
             (db/update-link db "a" "b" "leon")))
      (is (= {:shortlink "a" :target "b" :updated_by "leon" :usage 0} (db/get-link db "a")))
      (db/increment-usage! db "a")
      (is (= {:shortlink "a" :target "b" :updated_by "leon" :usage 1} (db/get-link db "a")))
      (is (= {:shortlink "z"} (db/get-link db "z"))))))

(deftest test-sqlite-reuse
  (with-tempfile [f]
    (let [db (-> f db/make-sqlite component/start)]
      (is (= {:shortlink "a" :target "b" :updated_by "leon" :usage 0 :modified? true}
             (db/update-link db "a" "b" "leon")))
      (component/stop db))
    (let [db (-> f db/make-sqlite component/start)]
      (is (= {:shortlink "a" :target "b" :updated_by "leon" :usage 0} (db/get-link db "a")))
      (component/stop db))))

;; Prerequisites:
;;   Run PostgreSQL locally
;;   psql -c 'create database jiggler_test;'
;;   psql -d jiggler_test < resources/sql/*.up.sql
;;   psql -d jiggler_test -c 'grant all on all tables in schema public to jiggler;'
;;   psql -d jiggler_test -c 'grant all on all sequences in schema public to jiggler;'
(deftest ^:integration test-postgresql
  (let [db (-> {:server-name "localhost" :port-number 5432 :database-name "jiggler_test" :username "jiggler" :password "jiggler"}
               db/make-postgresql
               component/start)
        shortlink (str "test_" (rand-int Integer/MAX_VALUE))]
    (is (= {:shortlink shortlink} (db/get-link db shortlink)))
    (is (= {:shortlink shortlink :target "b" :updated_by "leon" :usage 0 :modified? true}
           (db/update-link db shortlink "b" "leon")))
    (is (= {:shortlink shortlink :target "b" :updated_by "leon" :usage 0} (db/get-link db shortlink)))
      (db/increment-usage! db shortlink)
    (is (= {:shortlink shortlink :target "b" :updated_by "leon" :usage 1} (db/get-link db shortlink)))
    (is (= {:shortlink "z"} (db/get-link db "z")))))
