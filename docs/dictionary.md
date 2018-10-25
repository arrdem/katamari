# Dictionary
[⛓ README](/README.md)

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [Dictionary](#dictionary)
    - [Target](#target)
    - [Rule manifests](#rule-manifests)
    - [Build rule](#build-rule)
    - [Build products](#build-products)
    - [Rule inputs](#rule-inputs)
    - [Rollfile](#rollfile)
    - [Build graph](#build-graph)

<!-- markdown-toc end -->

## Target

A target is a name, which may be associated with a [build rule](#build-rule) within a [build graph](#build-graph).

For instance `me.arrdem/katamari` or `org.clojure/clojure` would be valid targets.

## Rule manifests

The interpretation of a [build rules](#build-rule) is defined by its `manifest`.
The manifest itself does nothing, it simply serves to define dispatching constant used when handing a rule.

## Build rule

A build rule is a list of a symbol.
The symbol is called the [rule manifest](#rule-manifest), and names the machinery which should be used to interpret the rule.
The rest of a rule is `keys*` arguments, which define the manifest's behavior.

For instance -

```clj
(clojure-library :deps {org.clojure/clojure nil} :paths ["src"])
```

would be a rule with the `clojure-library` manifest, parameterized with the `:paths` and `:deps`.
The symbol `clojure-library` would be the dispatch constant used by the roll API when handling the rule.

Rules are introduced by defining manifest types, and their parsing to rules.

## Build products

When building takes place, each build rule produces a product.
The product value serves to explain whatever was built, and allow downstream targets which depend on that product to consume it.
Typically this happens via [rule inputs](#rule-inputs), but products may also be transitively depended on.

For instance when building an Uberjar, all dependencies and other built products are transitively depended on.

## Rule inputs

Rules may list other rules as build inputs, and use the `rule-inputs` method to enumerate their dependencies.
When builds are executed, building takes place with both the inputs to the task, and all the existing build products.
This allows build steps to refer to all other built products, as well as to the products which they directly depend on.

For instance, the above `clojure-library` form would have no inputs - its only dependency is a Maven packaged artifact.

## Rollfile

Katamari uses files named `Rollfile` to define the [build graph](#buildgraph).
A Katamari project may consist of many rollfiles.
Each rollfile may define zero or more targets.

At present, all targets have global scope and can be referred to from any rollfile.

## Build graph

Katamari maintains a build graph.
The build graph maps [targets](#target) as defined by `deftarget` forms to the [build rule](#build-rule) which describes how to produce that target.

For instance if there was only one `Rollfile` -

```clj
(deftarget demo/demo
  (clojure-library :deps {} :paths ["src"]))
```

then the build graph would be a map of `demo/demo` to a [build rule](#build-rule) with the `clojure-library` [manifest](#manifest).
