(ns katamari.targets.clj
  "Clojure targets")

(defn clojure-library
  "Enters a Clojure library into the build.

  Targets which depend on this artifact directly or transitively will
  see this artifact included on classpaths when running Java tasks, or
  tasks which use the Java stack.

  FIXME: describe options. needs a spec."
  [build name options]
  (assoc-in build [:targets name]
            {:type :clojure-library
             :options options}))

(defn clojure-tests
  "Enters a Clojure test suite into the build.

  A Clojure test suite is just like a Clojure library, except that
  test running tasks will recognize it and the tests can be
  automatically run when the tests or any of their dependencies
  change.

  FIXME: describe options. needs a spec."
  [build name options]
  (assoc-in build [:targets name]
            {:type :clojure-tests
             :options options}))
