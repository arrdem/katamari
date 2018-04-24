# nrepl-python-client [![Travis CI status](https://secure.travis-ci.org/cemerick/nrepl-python-client.png)](http://travis-ci.org/#!/cemerick/nrepl-python-client/builds)

Surprisingly, an [nREPL](http://github.com/clojure/tools.nrepl) client
written in Python.

It pretty much works.

Requires Python 2.7 or 3.3.  Will work with any nREPL >= 0.2.0 endpoint that uses the
default bencode socket transport.  Support for [other
transports](https://github.com/clojure/tools.nrepl/wiki/Extensions#transports)
should be straightforward, thanks to an unpythonic multimethod thing that
`nrepl.connect()` uses.

## Installation

Clone from here and use the source, or you can install from
[PyPI](https://pypi.python.org/pypi/nrepl-python-client), e.g.:

```sh
$ easy_install nrepl-python-client
```

Or alternatively:

```sh
$ pip install nrepl-python-client
```

## Usage

Two options, currently.  First, explicit, synchronous send/receive of messages
to an nREPL endpoint:

```python
>>> import nrepl
>>> c = nrepl.connect("nrepl://localhost:58226")
>>> c.write({"op": "eval", "code": "(reduce + (range 20))"})
>>> c.read()
{u'session': u'7fb4b7a0-f9e5-4f5f-b506-eb2d0a6e21b1', u'ns': u'user', u'value': u'190'}
>>> c.read()
{u'status': [u'done'], u'session': u'7fb4b7a0-f9e5-4f5f-b506-eb2d0a6e21b1'}
```

`WatchableConnection` provides a facility vaguely similar to Clojure watches,
where a function is called asynchronously when an nREPL response is received if
a predicate or a set of pattern-matching criteria provided with that function
matches the response.  For example (from the tests), this code will
asynchronously capture `out` (i.e. `stdout`) content from multiple
sessions' responses:

```python
c = nrepl.connect("nrepl://localhost:58226")
wc = nrepl.WatchableConnection(c)
outs = {}
def add_resp (session, msg):
    out = msg.get("out", None)
    if out: outs[session].append(out)

def watch_new_sessions (msg, wc, key):
    session = msg.get("new-session")
    outs[session] = []
    wc.watch("session" + session, {"session": session},
            lambda msg, wc, key: add_resp(session, msg))

wc.watch("sessions", {"new-session": None}, watch_new_sessions)
wc.send({"op": "clone"})
wc.send({"op": "clone"})
wc.send({"op": "eval", "session": outs.keys()[0],
         "code": '(println "hello" "%s")' % outs.keys()[0]})
wc.send({"op": "eval", "session": outs.keys()[1],
         "code": '(println "hello" "%s")' % outs.keys()[1]})
outs
#>> {u'fee02643-c5c6-479d-9fb4-d1934cfdd29f': [u'hello fee02643-c5c6-479d-9fb4-d1934cfdd29f\n'],
     u'696130c8-0310-4bb2-a880-b810d2a198d0': [u'hello 696130c8-0310-4bb2-a880-b810d2a198d0\n']}
```

The watch criteria dicts (e.g. `{"new-session": None}`) are used to constrain
which responses received by the `WatchableConnection` will be passed to the
corresponding callbacks:

* `{"new-session": None}` will match any response that has any value in the
  `"new-session"` slot
* `{"session": session}` will match any response that has the value `session` in
  the `"session"` slot.

Sets may also be used as values in criteria dicts to match responses that
contain any of the set's values in the slot that the set is fond in the criteria
dict.

Finally, regular predicates may be passed to `watch()` to handle more complex
filtering.

The callbacks provided to `watch()` must accept three arguments: the matched
incoming message, the instance of `WatchableConnection`, and the key under which
the watch was registered.

## Send help

* Make this a _more_ Proper Python Library.  I've been away from Python for a
  loooooong time, and I don't know what the current best practices are around
  eggs, distribution, and so on.  The library is on PyPI, but may not be
  following the latest best practices for all I know.  Open an issue if you see
  a problem or some corner that could be made better.
* Fix my busted Python.  Like I said, the last time I did any serious Pythoning
  was in the 2.3 days or something (to use new-style classes, or not, that was
  the question, etc).  If I goofed, open an issue with a fix.

## Need Help?

Ping `cemerick` on freenode irc or
[twitter](http://twitter.com/cemerick) if you have questions or would
like to contribute patches.

## License

Copyright ©2013 [Chas Emerick](http://cemerick.com) and other contributors

Distributed under the MIT License. Please see the `LICENSE` file at the top
level of this repo.
