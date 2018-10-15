(ns katamari.targets
  (:require [clojure.spec.alpha :as s]))

(defmulti parse-target)
