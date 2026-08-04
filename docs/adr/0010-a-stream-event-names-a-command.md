# A stream event names a command

## Status

accepted

## Decision

A stream event's `event` field is a command keyword. Its `data` field is that
command's map as edn, without the `:command` key the event name already carries.

```
event: dom/morph
data: {:dom/content "<li id=\"row-4\">imported</li>"}
```

The client rebuilds the command and runs it through `run!`, the same seam
ADR-0004 defines for every other command:

```clojure
(rz/run! ctx (assoc (edn/read-string data) :command (keyword event-name)))
```

A stream is therefore a sequence of command invocations the server drives. It
introduces no vocabulary of its own.

## Considered options

- **A closed set of Tram-defined event names.** What the old runtime had, with
  `rhizome-patch-element` carrying `{:data {:elements [...]}}`. Rejected. It is a
  second vocabulary that has to grow a name every time the command set does, and
  an application cannot extend it at all. ADR-0004 made commands a registry so
  applications could add their own. A closed event table would put a wall around
  the one place a server most wants to reach.

- **One event name carrying a whole command map.** `event: rhizome` with
  `data: {:command :dom/morph ...}`. Rejected because it wastes the field the
  format already gives us and makes every event look identical in devtools. The
  network tab showing `dom/morph` and `signal/set` scrolling past is worth more
  than the symmetry.

## Consequences

- **Extending a stream costs nothing.** An application that registers
  `:app/confetti` can already have its server fire it down a stream. There is no
  second registration and no Tram change.

- **The server can run any registered command on the client.** That is the
  point, and it is also the whole of the trust model: the client executes what
  its own origin sends it. The commands are ones that origin's own page already
  registered, so a stream reaches nothing an html response could not. No
  allowlist. If a future command is dangerous enough to need one, the allowlist
  belongs on that command's definition, not on the transport.

- **The event name constrains command keywords to what an SSE field allows.** No
  newlines and no leading space. Every keyword satisfies this, so it costs
  nothing today, but a command key is now part of a wire format.

- **`data` is one line, always.** `pr-str` escapes newlines inside string
  literals, so an edn map serialises to a single line even when it holds html.
  The client still joins multiple `data:` lines with `\n`, because the format
  permits them and a parser that assumes otherwise is a parser that breaks on the
  first thing that is not us.

- **Unknown commands and malformed edn drop one event, not the stream.** They go
  to `:on-error`, matching how milestone 2 treats one bad element in a fragment.
