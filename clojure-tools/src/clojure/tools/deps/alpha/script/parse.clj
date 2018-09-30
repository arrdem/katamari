;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.tools.deps.alpha.script.parse
  (:require
    [clojure.java.io :as jio]
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.tools.deps.alpha.reader :as reader])
  (:import
    [java.io File]))

(defn parse-files
  "Parses a string of comma-delimited files into a collection of
  Files, filtering only those that exist."
  [s]
  (->> (str/split s #",")
    (map jio/file)
    (filter #(.exists ^File %))))

(defn parse-kws
  "Parses a concatenated string of keywords into a collection of keywords
  Ex: (parse-kws \":a:b:c\") ;; returns: (:a :b :c)"
  [s]
  (->> (str/split (or s "") #":")
    (remove str/blank?)
    (map
      #(if-let [i (str/index-of % \/)]
         (keyword (subs % 0 i) (subs % (inc i)))
         (keyword %)))))

(defn parse-config
  "Parses a string of edn into a deps map."
  [s]
  (#'reader/canonicalize-all-syms  ;; to be removed in the future
    (edn/read-string s)))