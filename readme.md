# Tram

<img src="./resources/images/readme-logo.png" width="300" alt="A tramcar with the Clojure logo on it">

> TRAM :: Tools for Rapid Application Modeling

Tram is an opinionated Clojure web framework, inspired by Ruby on Rails, to help
you quickly and joyfully build web applications. Tram provides default
libraries, scaffolding that wires them together, and a CLI tool for managing 
familiar tasks.

Tram is for you if you've ever wanted to use Clojure but been overwhelmed by the
amount of upfront learning required.  You shouldn't need to select 20 libraries
and wire them together yourself just to write a Hello World application.

> [!Note]
> Tram is in early alpha and not ready for production use

## Development (Fix this)

The workflow is atypical. Go to the application context where the daemon needs
to run. 

Start a nrepl server you want to connect to.  I use this for cider

```shell
clojure -Sdeps \{\:deps\ \{nrepl/nrepl\ \{\:mvn/version\ \"1.3.1\"\}\ cider/cider-nrepl\ \{\:mvn/version\ \"0.53.2\"\}\ refactor-nrepl/refactor-nrepl\ \{\:mvn/version\ \"3.10.0\"\}\}\ \:aliases\ \{\:cider/nrepl\ \{\:main-opts\ \[\"-m\"\ \"nrepl.cmdline\"\ \"--middleware\"\ \"\[refactor-nrepl.middleware/wrap-refactor\,cider.nrepl/cider-middleware\]\"\]\}\}\} -M:dev:test:cider/nrepl
```

After that is running, connect to it from your editor, and execute `(tram.daemon/-main)`, and you should be good to eval and live code.

## Prior Art Acknowledgements

Big thanks to these projects for inspiration and some code. I try to annotate
any code that is either directly or indirectly lifted from another project in
the ns docstring. If you think I've left anything out, please either email me or
file an issue and I'll happily add an attribution.

Biff, Kit, and Luminus are all fully fledged Clojure web frameworks that are
fantastic, but didn't quite scratch my itch. Check them out if you think Tram
might not be for you (or if you think it is).

