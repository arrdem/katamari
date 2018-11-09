# Katamari
<img align="right" src="/etc/katamari.jpg" width=300/>
<img aligh="left" src="https://img.shields.io/github/release/arrdem/katamari.svg" />

> King of All Cosmos: We are moved to tears by the size of this thing.

**TL;DR** A file sets based build tool. In Clojure, for Clojure.

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [Katamari](#katamari)
    - [Quickstart](#quickstart)
        - [Project demo](#project-demo)
        - [Tasks](#tasks)
    - [Documentation](#documentation)
    - [License](#license)

<!-- markdown-toc end -->

## Quickstart

From the [releases](https://github.com/arrdem/katamari/releases/latest) page, download the latest `kat`.

The `kat` script is self-bootstrapping.
Place it somewhere on your path, I like `~/bin/kat`.

Then from within a git repo, run

```
$ kat start-server
Starting server ...
Waiting for it to become responsive \
Started server!
  http port: 3636
  nrepl port: 3637
```

This will prompt Katamari to self-bootstrap, downloading the latest server standalone jar and creating a couple files in the root of your repository.
You'll see a `kat.conf`, a `kat-deps-defaults.edn` and a `kat-deps-resolve.edn`, as well as the new directories `.kat.d` and `target`.

The `kat.conf` file is a simple key-value config file used to configure both the `kat` shell client, and consumed by the server when handling requests.
It sets important properties like what the entrypoint namespace is, and what ports the server should attempt to use.

The files `kat-deps-defaults.edn` and `kat-deps-resolve.edn` are `deps.edn` data, and their names are specified in `kat.conf` so you can rename them however you wish as long as `kat.conf` points to them.
Respectively, they provide default dependency data and a default profile used by Katamari when building classpaths.
One possible use case is to provide `:default-versions` in `kat-deps-resolve.edn`, so that by referring to libs at the coordinate `nil` your targets can get the repo pinned default version of the lib.

The `.kat.d` directory is Katamari's workspace.
It contains files like the bootstrap jar, the server's logfile(s), any cached information and any build history.
You should be able to add it to your `.gitignore` and not touch it.
If you feel the urge to rename it, you can as long as `kat.conf` points to it.

The `target` drectory is the default location for build products.
When you build something with Katamari, it'll wind up there.

Okay.
So now lets build something!

### Project demo

A Katamari project is a git repository containing one or more files named `Rollfile`.
Rollfiles are somewhat like Makefiles - they define a graph of targets and their production rules.

In Leiningen, one could write the following `project.clj` -

```clj
(defproject simple-app
  :java-sources ["src/jvm/main"]
  :sources ["src/clj/main"]
  :dependencies [[org.clojure/clojure "1.9.0"]]
```

When Leiningen operates on this project, it will first use `javac` to produce the classfiles for your Java sources, and then do whatever you asked for in a classpath containing those classfiles, your Clojure sources and dependencies.
But this is clearly a two step process.
First you `javac`, then you work with the Clojure application.

In Katamari, this dependency is explicit.
One could write the following rollfile -

```clj
(deftarget my/java-library
  (java-library
   :paths ["src/main/jvm"]))

(deftarget my/simple-app
  (clojure-library
   :paths ["src/clj/main"]
   :deps {my/java-library nil
          org.clojure/clojure nil}))
```

The targets don't have to be dependency ordered, but it's convenient to write them as such.

Katamari, like Leiningen, will compile your Java sources before doing anything with your Clojure target.
However this dependency tree can go as deep as you would like it to.
Your Java target could in turn depend on another Java target, or a Kotlin target, or what have you.

Katamari is designed to enable incremental builds wherever possible.
This means that, unless there's no cache data available, Katamari should never do a full rebuild of your project(s).
For instance in this example, if there were no changes to the definition of your Java library, its dependencies or its files, why should it be rebuilt?
It's already "up to date".

### Tasks

The Katamari CLI client implements several tasks - operations implemented either on the server side or as a hybrid of server and client actions.
We can see what tasks Katamari is aware of by issuing the `list-tasks` (builtin) command.

```
$ ./kat list-tasks
Commands:
  compile
  help
  list-targets
  list-tasks
  meta
  restart-server
  show-request
  start-server
  stop-server
```

The most significant of these are probably `show-request`, which allows you to inspect both your build graph and configuration, `list-targets` which gives you a quick way to see what targets are visible in the project and `compile` which is used to compile targets and their dependencies.

For instance in Katmari's own repository, the `example` tree is used to define examples of all of Katamari's available targets.
That tree has a couple targets - `example/javac`, `example/clj`, `example/clj+jar` and `example/clj+uberjar`.

Lets try building `example/clj+uberjar` - 

```
$ ./kat compile example/clj+uberjar
{
  "example/javac": {
    "type": "katamari.roll.extensions.jvm/product",
    "from": "example/javac",
    "mvn/manifest": "roll",
    "deps": null,
    "paths": [
      "/private/var/folders/z4/6b9f3h4x2dv6gvxbwyc55cwnwhsd5r/T/javac6828446538002526748"
    ]
  },
  "example/clj": {
    "type": "katamari.roll.extensions.clj/product",
    "from": "example/clj",
    "mvn/manifest": "roll",
    "paths": [
      "/Users/arrdem/katamari/example/src/main/clj"
    ],
    "deps": {
      "example/javac": null,
      "org.clojure/clojure": null
    }
  },
  "example/clj+uberjar": {
    "type": "katamari.roll.extensions.jar/product",
    "from": "example/clj+uberjar",
    "mvn/manifest": "roll",
    "paths": [
      "/Users/arrdem/katamari/target/clj-standalone.jar"
    ],
    "deps": {
      "example/javac": null,
      "example/clj": null
    }
  },
  "intent": "json"
}
```

This is a build product - or more specifically it's all the build products for all the artifacts in the compilation graph of `example/clj+uberjar`.
`clj+uberjar` depends on the `clj` target, which depends on `javac`.
When compiling `clj+uber`, first `javac` is compiled to produce a directory of `.class` files, visible in the `"paths"` array of the `javac` result.
That result is consumed when producing the `clj` result, and all of those paths (and the depended jars) get packed into the file `target/clj-standalone.jar` if you care to unzip it and look.

Note that unlike Leiningen and other tools, Katamari makes no attempt to automate generating manifests - at least not yet.
Jar manifests are dependencies which must be built and included when packing a jar, just like any other dependency.

## Documentation

- [Demo talk @ SF Clojure](https://www.youtube.com/watch?v=ze7OI9iVCiI)
- [Demo talk on the Apropos Cast](https://youtu.be/e-49mgpYBe8?t=2136)
- [Manifesto](/docs/manifesto.md)
- [System Architecture](/docs/system-architecture.md)
- [Build Graph & Targets](/docs/build-graph.md)

## License

Copyright © 2018 Reid "arrdem" McKenzie

Distributed under the [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html) either version 1.0 or (at your option) any later version.
