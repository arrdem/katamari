(ns katamari.steps.classpath
  "The classpath step.

  Primary entry point for JVM library and resource providers."
  (:require [katamari.steps.impl :refer [defstep]]
            [clojure.string :as str]))

(defstep `classpath
  "Accumulates classpaths to a single classpath."
  :katamari.steps.modes/closure
  #{::classpath}
  ::classpath
  (fn [_opts classpaths]
    (str/join ":" classpaths)))
