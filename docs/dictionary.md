# Dictionary
[⛓ README](/README.md)

## Target

A target is a name, which may be associated with a [build rule](#buildrule) within a [build graph](#buildgraph).

For instance `me.arrdem/katamari` or `org.clojure/clojure` would be valid targets.

## Rule manifests

The interpretation of a [build rules](#buildrule) is defined by its `manifest`.
The manifest itself does nothing, it simply serves to define dispatching constant used when handing a rule.

## Build rule

A build rule is a list of a symbol.
The symbol is called the [rule manifest](#rulemanifest), and names the machinery which should be used to interpret the rule.
The rest of a rule is `keys*` arguments, which define the manifest's behavior.

For instance -

```clj
(clojure-library :deps {org.clojure/clojure nil} :paths ["src"])
```

would be a rule with the `clojure-library` manifest, parameterized with the `:paths` and `:deps`.
The symbol `clojure-library` would be the dispatch constant used by the roll API when handling the rule.

Rules are introduced by defining manifest types, and their parsing to rules.

## Rollfile

Katamari uses files named `Rollfile` to define the [build graph](#buildgraph).
A Katamari project may consist of many rollfiles.
Each rollfile may define zero or more targets.

At present, all targets have global scope and can be referred to from any rollfile.

## Build graph

Katamari maintains a build graph.
The build graph maps [targets](#target) as defined by `deftarget` forms to the [build rule](#buildrule) which describes how to produce that target.

For instance if there was only one `Rollfile` -

```clj
(deftarget demo/demo
  (clojure-library :deps {} :paths ["src"]))
```

then the build graph would be a map of `demo/demo` to a [build rule](#buildrule) with the `clojure-library` [manifest](#manifest).
