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
to run. Jack in a regular cider project there, then require tram, and run the
daemon with `(tram.daemon/-main)`, and you should be good to eval and live code.
