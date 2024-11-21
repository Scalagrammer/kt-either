package scg.kt.either

import kotlin.coroutines.*

typealias Comprehension<L, R> = suspend ComprehensionScope<L, R>.() -> R

@RestrictsSuspension
sealed interface ComprehensionScope<L, R> {

    suspend fun L.yieldAsLeft() : Nothing

    suspend fun R.yield() : Nothing

    suspend fun <T> Either<L, T>.bind() : T =
        when (this) {
            is Right<T> -> value
            is Left<L> ->
                value.yieldAsLeft()
        }

    suspend operator fun <T> Either<L, T>.component1() : T =
        this.bind()

    suspend fun <T> Iterable<Either<L, T>>.bindAll() : List<T> =
        this.map { it.bind() }
}

suspend fun <L, R> ComprehensionScope<L, R>.ensure(condition : Boolean, unconditional : () -> L) {
    when { !condition -> (unconditional()).yieldAsLeft() }
}

suspend fun <L, R> ComprehensionScope<L, R>.ensureNot(condition : Boolean, unconditional : () -> L) {
    when { condition -> (unconditional()).yieldAsLeft() }
}

internal class ComprehensionScopeImpl<L, R> : ComprehensionScope<L, R>, Continuation<R> {

    override val context = EmptyCoroutineContext

    private lateinit var yield : Either<L, R>

    override fun resumeWith(result: Result<R>) {
        yield = (result.getOrThrow()).pure()
    }

    override suspend fun R.yield(): Nothing =
        suspendCoroutine { yield = pure() }

    override suspend fun L.yieldAsLeft(): Nothing =
        suspendCoroutine { yield = asLeft() }

    fun yield(): Either<L, R> = yield
}