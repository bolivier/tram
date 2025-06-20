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

## Development

The workflow is atypical. Go to the application context where the daemon needs
to run. Jack in a regular cider project there, then require tram, and run the
daemon with `(tram.daemon/-main)`, and you should be good to eval and live code.
