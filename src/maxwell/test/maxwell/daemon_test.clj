(ns maxwell.daemon-test
  (:require [maxwell.daemon :refer :all]
            [clojure.test :refer :all]))

(deftest test-map-diff
  (-> {}
      (with-diff)
      (assoc :foo 1
             :bar 2)
      (update :foo inc)
      (dissoc :bar)
      (get-diff)
      (= [[:insert :foo nil 1 nil]
          [:insert :bar nil 2 nil]
          [:change :foo 1 2 nil]
          [:remove :bar 2 nil nil]])
      is))

(deftest test-nested-update-diff
  (-> {}
      (with-diff)
      (assoc-in [:foo :foo] 1)
      (assoc-in [:foo :bar] 2)
      (assoc-in [:foo :baz] 2)
      (get-diff)
      ;; This is the current behavior, and the trivially correct behavior.
      ;; Is it desired?
      ;; Technically it reflects the precise update behavior.
      ;; It is not however the desired or minimal update behavior.
      (= [[:insert :foo nil {:foo 1} nil]
          [:change :foo {:foo 1} {:foo 1, :bar 2} nil]
          [:change :foo {:foo 1, :bar 2} {:foo 1, :bar 2, :baz 2} nil]])
      is))

(deftest test-vector-diff
  (-> []
      (with-diff)
      (conj 1)
      (conj 2)
      (conj 3)
      (assoc 0 4)
      (get-diff)
      (= [[:insert 0 nil 1 nil]
          [:insert 1 nil 2 nil]
          [:insert 2 nil 3 nil]
          [:change 0 1 4 nil]])
      is))
