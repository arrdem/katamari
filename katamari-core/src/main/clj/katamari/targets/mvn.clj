(ns katamari.targets.mvn
  "The Maven target.

  Integrates with the classpath step.")

(defn mvn-dep
  "Enters a Maven jar dependency into the build.

  FIXME: describe options. needs a spec."
  ([build coordinate]
   (mvn-dep build coordinate {}))
  ([build [name version & more :as coordinate] options]
   (assoc-in build [:targets name]
             {:type :maven-dep
              :options (assoc-in options [:base :coordinate] coordinate)})))

(defn mvn-artifact
  "Enters a jar with Maven coordinates into the build.

  Targets which depend on this artifact directly or transitively will
  see this artifact included on classpaths when running Java tasks, or
  tasks which use the Java stack.

  FIXME: describe options. needs a spec."
  [build name options]
  (assoc-in build [:targets name]
            {:type :maven-artifact
             :options options}))
