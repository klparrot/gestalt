(ns ^{:author "Montoux Limited, <info@montoux.com>"
      :doc "N/A"}
  test.montoux.gestalt
  (:use clojure.test)
  (:require [montoux.gestalt :as gestalt]
            [clojure.java.io :as jio])
  (:import (java.io File StringReader)))


(deftest test-with-config-with-environment
  (gestalt/with-config {:test-env {::foo 42}
                        :another-env {::foo 3.14}}
    (gestalt/with-environment :test-env
      (is (= 42 (gestalt/get ::foo))))
    (gestalt/with-environment :another-env
      (is (= 3.14 (gestalt/get ::foo))))))

(deftest test-with-config-temporary
  (gestalt/with-environment :test
    (gestalt/with-config {:test {:foo 42 :bar :baz}}
      (is (= 42 (gestalt/get :foo)))
      (gestalt/with-config {:test {:foo 3.14 :bar :x}}
        (is (= 3.14 (gestalt/get :foo))))
      (is (= 42 (gestalt/get :foo))))))

(deftest test-with-config-and-reset
  (gestalt/reset-gestalt!
   (StringReader. "{:test {:foo 1 :bar 2} :test2 {:foo 10 :bar 20}}") :test2)
  (is (= 10 (gestalt/get :foo)))
  (gestalt/with-scoped-config
    (gestalt/reset-gestalt!
     (StringReader. "{:test {:foo 1 :bar 2} :test2 {:foo 42 :bar 20}}") :test2)
    (is (= 42 (gestalt/get :foo)))
    (gestalt/with-environment :test
      (is (= 1 (gestalt/get :foo)))))
  (is (= 10 (gestalt/get :foo))))

(deftest test-reset-gestalt!
  (gestalt/with-scoped-config
    (gestalt/reset-gestalt!
     (StringReader. "{:test {:foo 1 :bar 2} :test2 {:foo 10 :bar 20}}") :test)
    (is (= 1 (gestalt/get :foo)))))

(defn- tmpfile [contents]
  (doto (File/createTempFile "cfg-test" ".clj")
    (.deleteOnExit)
    (spit contents)))

(defmacro with-system-property
  "Temporary sets system property k to value v.
   restores the old value before returning.
   NB: this modifies Java system properties and is NOT THREAD SAFE!"
  [k v & body]
  `(let [old-value# (System/getProperty ~k)]
     (try
       (System/setProperty ~k ~v)
       ~@body
       (finally
        (if (nil? old-value#)
          (System/clearProperty ~k)
          (System/setProperty ~k old-value#))))))

(deftest test-automatic-reset-gestalt
  (let [f (tmpfile "{:test {:foo 1 :bar 2} :test2 {:foo 10 :bar 20}}")]
    (try
      (gestalt/with-scoped-config
        (with-system-property gestalt/GESTALT_CONFIG_FILE_PROP (str f)
          (with-system-property gestalt/GESTALT_ENVIRONMENT_PROP "test2"
            (is (= 10 (gestalt/get :foo)) "initialised on first use")
            (is (= :test2 (gestalt/environment))))
          
          (with-system-property gestalt/GESTALT_ENVIRONMENT_PROP "test"
            (is (= 10 (gestalt/get :foo)) "not initialised again")
            (is (= :test2 (gestalt/environment))))))
      
      (finally
       (jio/delete-file f)))))

(deftest test-contains?
  (gestalt/with-config {:test {:foo 42 :a {:b {:c 1}} :vec [0] :falsey false :null nil}}
    (gestalt/with-environment :test
      (is (not (gestalt/defined? nil)))
      (is (not (gestalt/defined? :bar)))
      (is (not (gestalt/defined? :vec 1)))
      (is (not (gestalt/defined? "foo")))
      (is (gestalt/defined? :foo))
      (is (gestalt/defined? :a :b :c))
      (is (gestalt/defined? :vec 0))
      (is (gestalt/defined? :falsey))
      (is (gestalt/defined? :null))
      )))

(deftest test-get
  (gestalt/with-config {:test {:foo 42 :a {:b {:c 1}} :vec [0] :falsey false :null nil}}
    (gestalt/with-environment :test
      (is (thrown? IllegalArgumentException (gestalt/get nil)))
      (is (thrown? IllegalArgumentException (gestalt/get :bar)))
      (is (thrown? IllegalArgumentException (gestalt/get :vec 1)))
      (is (thrown? IllegalArgumentException (gestalt/get "foo")))
      (is (= 42 (gestalt/get :foo)))
      (is (= 1 (gestalt/get :a :b :c)))
      (is (= 0 (gestalt/get :vec 0)))
      (is (= false (gestalt/get :falsey)))
      (is (= nil (gestalt/get :null)))
      )))
