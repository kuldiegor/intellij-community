// "Create extension function 'foo'" "true"

trait T

fun test(t: T) {
    val b: Boolean = t.foo("1", 2)
}

fun T.foo(s: String, i: Int): Boolean {
    throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
}
