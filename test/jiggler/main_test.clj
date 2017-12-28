;; Copyright (c) 2017 Yieldbot
(ns jiggler.main-test
  (:require
   [clojure.java.io :as jio]
   [clojure.test :refer :all]
   [com.stuartsierra.component :as component]
   [jiggler.db :as db]
   [jiggler.main :as main]
   [jiggler.routes :as routes]))

(deftest test-errors
  (binding [*out* (jio/writer "/dev/null")]
    (with-redefs [main/exit! identity]
      (is (= 64 (main/-main "--not-an-arg"))))))

(deftest test-help
  (binding [*out* (jio/writer "/dev/null")]
    (with-redefs [main/exit! identity]
      (is (= 2 (main/-main "--help"))))))

(deftest test-arg-parsing
  (let [system (atom nil)]
    (with-redefs [main/start #(reset! system %)
                  routes/wait-for-webserver (constantly nil)]
      (testing "sqlite"
        (main/-main "--sqlite" "sqlite-file.db")
        (is (= #{:database :routes :webserver}
               (set (keys @system))))
        (is (= "sqlite-file.db" (get-in @system [:database :dbfile]))))
      (testing "postgresql"
        (main/-main "--database-host" "host"
                    "--database-port" "5555"
                    "--database-db" "mydb"
                    "--database-username" "user"
                    "--database-password" "pass")
        (is (= #{:database :routes :webserver}
               (set (keys @system))))
        (is (= {:server-name "host"
                :port-number 5555
                :database-name "mydb"
                :username "user"
                :password "pass"
                :pool nil}
               (into {} (:database @system))))))))
