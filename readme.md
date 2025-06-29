# Tram

<img src="./resources/images/readme-logo.png" width="300" alt="A tramcar with the Clojure logo on it">

> TRAM = Tools for Rapid App Modeling
> 
>   or
> 
>   TRAM = The Rails Alternative for Makers
> 
>   or
> 
>   TRAM = Tiny Rails-ish Application Machine

Tram is an opinionated Clojure web framework, inspired by Ruby on Rails, to help
you quickly and joyfully build web applications. Tram provides default
libraries, scaffolding that wires them together, and a CLI tool for managing the
familiar tasks.

Tram is for you if you've ever wanted to use Clojure but been overwhelmed by the
amount of upfront learning required.  You shouldn't need to select 20 libraries
and wire them together yourself just to write a Hello World application.

## Development (Fix this)

The workflow is atypical. Go to the application context where the daemon needs
to run. 

Start a nrepl server you want to connect to.  I use this for cider

```shell
clojure -Sdeps \{\:deps\ \{nrepl/nrepl\ \{\:mvn/version\ \"1.3.1\"\}\ cider/cider-nrepl\ \{\:mvn/version\ \"0.53.2\"\}\ refactor-nrepl/refactor-nrepl\ \{\:mvn/version\ \"3.10.0\"\}\}\ \:aliases\ \{\:cider/nrepl\ \{\:main-opts\ \[\"-m\"\ \"nrepl.cmdline\"\ \"--middleware\"\ \"\[refactor-nrepl.middleware/wrap-refactor\,cider.nrepl/cider-middleware\]\"\]\}\}\} -M:dev:test:cider/nrepl
```

After that is running, connect to it from your editor, and execute `(tram.daemon/-main)`, and you should be good to eval and live code.
