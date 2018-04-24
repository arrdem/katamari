(ns katamari.steps.impl
  "The common implementation framework for steps."
  (:refer-clojure :exclude [apply name])
  (:require [clojure.spec.alpha :as s]
            [detritus.spec :refer [deftag]]))

(defonce +step-registry+
  "A registry of all the known steps."
  (atom []))

(defn step-registry
  "Getter for the current state of the step registry."
  [] (into {} (map (juxt :name identity) @+step-registry+)))

(s/fdef defstep
  :args (s/cat :name qualified-ident?
               :docs? (s/? string?)
               :meta? (s/? map?)
               :mode #{:katamari.steps.modes/target
                       :katamari.steps.modes/closure}
               :selector ifn?
               :product qualified-keyword?
               :implementation ifn?))

(defn defstep
  "Defines a single Katamari build step."
  {:style/indent 1
   :arglists '([name doc? meta? mode selector product implementation])}
  [& args]
  (let [the-spec (:args (s/get-spec `defstep))
        args (s/conform the-spec args)]
    (assert (not= ::s/invalid args)
            (s/explain-str the-spec args))
    (let [step (assoc args :type :katamari/step)]
      (swap! +step-registry+ conj step)
      step)))
