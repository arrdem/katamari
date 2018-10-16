# Manifesto

Build tools are tricky things.
It's often said that the [two hardest problems in computer science are cache invalidation, naming things and off by one errors](https://twitter.com/codinghorror/status/506010907021828096).
Unfortunately, build tools run right smack into both^W all of these problems.
Build targets / products must be named for human use, compilation results must be cached so programmers don't tear their hair out waiting for compilers and all sorts of otherwise reasonable assumptions are ruined by flaky tests and non-reproducible builds.

Programmers, well adapted to wrangling complexity and pipelines, do all kinds of things when packaging and building their software.
Build systems are in the most common case mere shell scripts used to glue together programs of all kinds.
The essential problem of gluing together Unix programs is that each program could have arbitrary effects.
There's no good way to understand what configuration a task consumed, or what results it produced.

"real" build tools try to solve this problem by rendering build steps declarative, so that the declared dependency relationships can be used to derive incremental compilation behavior.
This is the approach taken by Bazel, Buck, Pants, Maven, Boot and more.

As a Twitter employee I spent a lot of "quality" time with Pants and came to appreciate the repeatability properties it provided, and its change detection facilities.
Unfortunately because Pants and friends try to be efficient general purpose solutions for many toolchains, their particular machinery is greatly complicated by the needs of those tools.
For instance Pant's Java compilation pipeline is inextricably bound to Scala's incremental compilation engine because that was a high priority for the Pants developers.
As these tools are institutional, they're thinly documented at best.
All their primary customers are internal, and cultivating an external user base is nobody's priority.

Many build tasks are honestly pretty trivial, and just get re-computed because it's more engineering effort than it's worth to write special purpose change detection machinery.
The hope of Katamari is that, by "firing your customers" the size of the problem domain can be meaningfully constrained and a simple system with adequate performance can be produced just by leveraging content addressing of build products.

## In contrast to deps

Katamari isn't the only tool with this idea at the end of the day - `deps.edn` AKA `clojure.tools.deps(.alpha)` shares at least some of the same motivation.
The unfortunate thing is that in its focus on the classpath as the essential unit, `deps.edn` offloads to the user as mere details such unfortunate realities as running tests and producing artifacts based on the classpath.
The tacit official recomendation for deploying `deps.edn` seems to be to `Makefile` or otherwise shell script `deps.edn` which solves one problem together with whatever else you may need.
`deps.edn` is no help for these tasks.

Katamari's design is less aescedic in its minimalism.
The reality is that users' workflows between the REPL, running stand-alone tests and producing artifacts are related and benefit from integration as they depend fundimentally on the same dependency data.
Maintaining simplicity isn't easy when trying to solve these problems, but solutions are more generally useful and impose less upon their users to pick up the pieces.

Also `deps` just leaves a lot on the table in terms of its caching strategy, and eschews the entire problem of multi-language builds.

## In contrast to Leiningen

Lein does what I want really nicely for small projects, but its lack of caching and fundimental boot jvm, process, kill JVM architecture puts a 3-5s lower bound on any given lein operation.
Checkouts don't really save you in a multi-module configuration because they still assume that you have artifacts of the same name installed and resolvable via Maven.
This makes fully anonymous / source builds at best difficult if not impossible.

## In contrast to Boot

Boot is designed to solve the related problem of incremental file rebuilds also using a durable server and does so nicely.
Unfortunately, Boot falls squarely into the single module at a time trap and there doesn't seem to be any recognizable prior art for multi-module boot setups.
Multi-module is a hard design goal.

## In contrast to Gradel

Gradel is a more mature tool which supports programmable builds and has many existing extensions for languages including Clojure and Java.
If you're comfortable with Groovy, it's probably the best way to go here.
Katamari was built in large part out of what Alex Miller characterized as a "fear of XML" and in this case of Groovy.

I'm a Clojure developer, working on a Clojure team none of whom have any experience with the Grade/Groovy toolchain.
The barrier to enntry for hacking on it or adapting it to our needs is fairly vertical, whereas the core algorithms and caching strategies behind build systems are fairly well understood and not hard to impleent.
