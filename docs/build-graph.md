# Build Graph & Targets
[⛓ README](/README.md)

At its most basic, the Katamari build model consists of "tasks", "targets" and "products".
A **product** is just a fancy term for one or more files.
Products are produced by **tasks**, which should be pure functions from products and other configuration to products.
Products are anonymous by default, but may be given names.
Named products are called **targets**.

Lets take an example.
I want to automate the build for a Clojure application.
My application has some resource and source files locally.
I've got some Maven dependencies which need to be included.
I've got some Java sources which need to be compiled, and which probably have some Maven dependencies themselves.
Finally I've got some other program which generates resource files I need to run my application.
Maybe I'm downloading data files from s3 or using some in-house tool to generate config.

Traditionally in [leiningen](https://github.com/technomancy/leiningen), one would use [`:prep-tasks`](https://github.com/technomancy/leiningen/blob/master/sample.project.clj#L258-L262) to provide a sequence of tasks such as `javac` which will be executed before a goal such as `repl` or `test`.
While this works, it's less than ideal in that all the tasks have to implement their own caching and change detection.
Most don't at all, which makes the "simple" thing while correct often quite inefficient.

By introducing a concrete concept of products and their dependency graph, the various Google Blaze derivatives all nicely sidestep this issue.
Products are cached and need not be re-computed until they become invalidated by some change in the state of the system.

In Blaze/Buck/Pants, one could write a build descriptor file for such an artifact along these lines (these systems use Python for their configuration DSL)

```python
# The resource files for the build
java_resources(
  name="resource-files",
  files=glob("resources/**"),
)

# A Maven dependency, providing the "clojure" target
maven_dependency(
  name="clojure", 
  mvn_group="org.clojure",
  mvn_artifact="clojure",
  mvn_version="1.6.0",
  mvn_qualifier=None,
  repository="maven-central",
)

maven_dependency(
  name="kafka", 
  mvn_group="org.apache.kafka",
  mvn_artifact="kafka_2.11",
  mvn_version="...",
  mvn_qualifier=None,
  repository="confluent",
)

# Our Java sources
java_library(
  name="libfoo",
  dependencies=["clojure"], # a reference to the "clojure" target above
  files=glob("src/jvm/**.java"),
)

# Our Clojure sources
fileset(
  name="foo-core-src",
  dependencies=["clojure"],
  files=glob("src/clj/**.cljc?"),
)

# Our Clojure sources as a library
clojure_library(
  name="foo-core",
  dependencies=["clojure", "libfoo"],
  files=glob("src/clj/**.cljc?"),
)

# Our shell-command produced resources, based on doing something to the foo-core-src fileset.
shell_command(
  name="gen-resources",
  command=[....],
  dependencies=["foo-core-src"],
  files=glob("gen-resources/**"),
)

# Our application entirely
java_binary(
  name="foo",
  entry_point="foo.main",
  dependencies=["foo-core"], 
)
```

Consider the `java_library` product.
It's results are defined by `javac` over some set of source files, some options such as the target JVM version and some dependencies.
If we've never built for that combination before, we have to do so.
If we're building again and already have that build parameter combination lying around, then our build tool can just hit in a cache, assuming the build is repeatable and the cache is good.
However if one of the build inputs changed - say a source file was edited or a build parameter like the target JVM version was changed, then we have to do a new build.

There's plenty of room for refinement here.
Good 'ol GNU Make uses file change timestamps as the metric of whether a build product is "up to date".
This has some significant limitations, especially when using a version control system like git which can cause files to time travel.
The goal of Katamari and incremental build systems generally is to trade build artifact cache size on disk for rebuild rapidity wherever possible.
Using file timestamps is a poor heuristic in this regard, because it means that one throws away the build cache when simply changing branches.

Content-hashing (sha512sum or equivalent) is the minimum acceptable content identifier, in that the content identifiers are reasonably unique and repeatable across branch changes.
A design goal is to be pluggable with respect to content hashing algorithms.
In a C file for instance, whitespace changes and comment changes don't* affect the interpretation of the source files by the compiler - consequently one could imagine writing a domain specific content hash operation which considers only the hash of non-lexical-whitespace in the file.
Likewise for Lisp code, comments and whitespace are discarded.
One could imagine content hashing the s-expression structure of the file to produce an identifier - or having a two step content hashing operation where straight file hashes are used to cache more precise content hashes.

The precise build DSL which Katamari uses is up in the air, but it will be full blown Clojure, with targets and products registered as datastructures into a graph.
Content hashing of the Katakari `Rollfiles` themselves allows for lazy re-analysis of the build graph, and the build graph itself defines its own invalidation conditions.
