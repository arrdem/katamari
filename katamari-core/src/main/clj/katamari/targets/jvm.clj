(ns katamari.targets.jvm
  "JVM generic targets.")

(defn jar
  "Enters a \"jar\" into the build.

  Jars are produced incrementally, and contain only their direct
  dependencies.  For instance, a jar which depended on several Clojure
  targets would contain the

  FIXME: describe options. needs a spec."
  [build name options]
  (assoc-in build [:targets name]
            {:type :uberjar
             :options options}))

(defn uberjar
  "Enters an \"uberjar\" into the build.

  Unlike normal jars, uberjars include all their transitive
  dependencies and can be executed stand-alone.

  FIXME: describe options. needs a spec."
  [build name options]
  (assoc-in build [:targets name]
            {:type :uberjar
             :options options}))
