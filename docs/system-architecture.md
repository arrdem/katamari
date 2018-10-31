# System Architecture
[⛓ README](/README.md)

Katamari is designed in response to `clojure-tools`, `boot` and `leiningen`.

`clj`, `clojure` and `lein` are implemented as Clojure + JVM applications, which start and stop.
This means that in general users suffer all the pains of starting Clojure based systems and really don't see many of the benefits.
Clojure itself takes a second or two to load on average, and every additional dependency just adds weight.
Furthermore in order to take advantage of graalvm or other Java optimizing tools it's imperative to strip the dependencies of these tools down which prevents their implementations from leveraging library ecosystems.

`boot` is designed to be used somewhat persistently, as boot builds consist of watch-based programs and the initial startup and dependency loading time is amortized over a session of sorts.
This makes boot more tractable, but boot was designed and optimized for solving the problem of updating built resources such as `less` files and `cljs` to be served in a browser.
Because in the context of a webapp all these resources must converge in the application, boot is not designed for a concept of targets or to support "partial" builds.

## Kat client

Katamari is designed as a small shell script - `kat`, which interacts with a persistent JVM server.
The shell script serves as a shim, which starts a server instance if there isn't an active one, and then uses the server to perform any required actions.

### Tasks

Beyond this, Katamari's architecture is currently somewhat murky.
As the Katamari server is intended to be durable, while it embeds a REPL (including CIDER), it's not really an appropriate vehicle from which to execute build steps.
These would seem more naturally and more interactively placed close to or even in the user's shell, rather than having the remote server opaquely and non-interactively execute many commends.

There would also seem to be cause to enable users to write their own shell scripts which interact with the Katamari server.
For instance one could imagine a `kat status` task, implemented as a shell script which uses the normal `kat` machinery to inspect the server, and can avoid the default server starting behavior.

### Intents

At present, the Katamari client integrates with the server using an intents mechanism.
The user may request an output format from the client - one of the following:

- `-j` or `--json`, force JSON pretty printing based on `jq`
- `-r` or `--raw`, directly display the response
- `-m` or `--message`, format the `.message` of the response

Unless the user specifically requests a response handling, the server can control it.
This is done by responding with a JSON body, containing the `.intent` key.
The intents `raw`, `json`, `msg`, `message`, `sh`, `subshell` and `exec` are supported.

The `sh` and `subshell` intents will cause the `kat` client to execute the `.sh` response field as a BASH script.
The `exec` intent will cause the client to well `exec` the `.exec` response field.
These allow the server to express intents such as a remote restart, running a CLI REPL client or a test suite which are at best awkward from a fully remote server.

## Katamari server

The Katamari server is a full Ring application running embedded nREPL + CIDER for debugging and development support.
The server provides only two routes at present - `/api/v0/ping` used only to establish the server's liveness, and `/api/v0/request` which is used to process requests from the `kat` script.

When the user enters a command - say `./kat start-server` which shows server port information - this becomes essentially a

```
$ curl -XPOST localhost:3636/api/v0/ping --data @- <<EOF
{"repo_root": "...",
 "user_root": "...",
 "config_file": "...",
 "cwd": "$PWD",
 "request": [$@]}
EOF
```

allowing for some differences of implementation to correctly escape things.

### Middlewares

The Katamari server uses two middleware stacks to process requests.
The first is the Ring side mdidleware stack, which behaves exactly as you'd expect.
The second is the Katamari server extensions stack - used only to implement handling of tasks and wrapping of tasks on the server.
These middleware stacks were separated from each other so that tasks could recurse and manipulate each other more finely.
Whether that pans out is somewhat up in the air.

Task middlewares come in two flavors `katamari.server.extensions/defhandler` and `defwrapper`.
`handlers` are used to implement handing a single task - or passing along an unrecognized task request.
`wrappers` don't handle tasks, but wrap up task handling with context.
For instance the build graph it itself a `wrapper` around the handler which provides the `compile` task.

The handler (and wrapper) contract is that they accept the `[handler, config, stack, request]` where the `handler` is really the next fn in the middleware stack, the `config` is the global configuration, the `stack` is the entire composed middleware as a fn in case you want to restart processing of a request, and the `request` is the `argv` as recieved from the client.

The `show-request` task is pretty typical -

```clj
(defhandler show-request
  "Show the request and config context as seen by the server (for debugging)"
  [handler config stack request]
  (->  {:intent :json
        :config config
        :request request}
       (resp/response)
       (resp/status 200)))
```

The advantage of these macros is that they tie into a dynamically re-computed middleware stack to better enable live development of handlers and wrappers without restarting the web server all the time.

## Rolling

Rolling - or more precisely the `katamari.roll.*` namespaces - are the core of Katamari's actual build machinery.
The API to these namespaces is inspired heavily by `clojure.tools.deps(.alpha)`, and is intended to enable the separate development of additional targets.

The roll API presents several multimethods for extension, visible in the `katamari.roll.extensions` namespace.
The overall intent is that users leverage the `defmanifest` macro to create new [rule manifests](dictionary.md#rule-manifests) so that they can be parsed in Rollfiles by the reader.

The rest of the API allows a manifest to define how it interacts with the build graph.
Implementations of most methods are required for every manifest type.
Only the `manifest-prep` and `rule-prep` methods are optional.

The roll API leverages `clojure.tools.deps(.alpha)` to implement classpath building, by way of a custom `:roll` deps manifest type which allows for control over how deps behaves.

## Build caching

At present, Katamari only implements (correct!) static dependency order builds.
Build product identification for minimal rebuilds is a work in progress.
