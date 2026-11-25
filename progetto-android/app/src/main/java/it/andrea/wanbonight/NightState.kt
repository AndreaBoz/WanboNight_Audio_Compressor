package it.andrea.wanbonight

// Shared by activity and service on the main thread. A new process starts inactive.
object NightState {
    var message = "Disattivato · audio originale"
    var active = false
    var mode = "stop"
}
