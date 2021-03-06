package burst.common

public data class Quad<out A, out B, out C, out D>(
        public val first: A,
        public val second: B,
        public val third: C,
        public val fourth: D
) {
    public override fun toString(): String = "($first, $second, $third, $fourth)"
}

public fun <T> Quad<T, T, T, T>.toList(): List<T> = listOf(first, second, third, fourth)