package time

import java.time.Duration

val Int.seconds: Duration
    get() {
        return Duration.ofSeconds(this.toLong())
    }

val Int.hours: Duration
    get() {
        return Duration.ofHours(this.toLong())
    }