# Modules source tree

As a proof of the concept, Katamari's repository is structured into several modules which live here below the `src` tree.

In Katamari, build targets are defined by rules within a `Rollfile`.
However, as all `Rollfile`s in a repository are analyzed to find targets, the precise location of `Rollfile`s and even of the source paths they use is immaterial to Katamari's functionality.
The design chosen here is that each "library" - being a rough partition of Katamari by functionality and dependencies - gets its own source tree.
This makes it easier as a human to navigate the repository, and to focus on editing related files.

For instance `maxwell` is a discrete library, dealing with implementing change tracking data structures.
It has no intimate knowledge of Katamari itself, and could even be in a separate repo were it stable enough.
However, as Katamari is `maxwell`'s primary consumer it is convenient to co-locate the two in a single repository even once the APIs are well understood.

Modules are generally structured either after clojure contrib.
projects - eg `src/main/clj`, `src/main/java`, `src/test/clj`, `src/test/java` for modules which have Java components or Leiningen style eg `src` and `test` when there is only Clojure code.
No style is proscribed or supported by default.

## Modules

- [clojure-tools](/src/clojure-tools) - the `clojure-tools` scripts & their backing `.clj` files
- [tools.deps](/src/clojure-tools-deps) - vendored `tools.deps`
- [maxwell](/src/maxwell) - change tracking data structures
- [roll](/src/roll) - the core rolling API used to implement compilation
- [roll-jvm](/src/roll-jvm) - implementations of `clojure-library` etc. atop the `roll` API.
- [katamari](/src/katamari) - the Katamari server itself as a client of `roll`
- [example](/src/example) - A demo module used in part to test Katamari

