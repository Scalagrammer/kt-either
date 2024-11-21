package scg.kt.either

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.suspendCoroutine

interface AtScope< R> {
    fun <A> definedAt(f : (A) -> R, cast : (Any) -> A?) : Boolean
}

suspend inline fun <reified A, R> AtScope<R>.at(noinline f : (A) -> R) {
    when { definedAt(f) { it as? A } -> suspendCoroutine<Unit> { Unit } }
}

internal class AtScopeImpl<R>(private val at : Any): AtScope<R>, Continuation<Unit> {

    override val context = EmptyCoroutineContext

    private lateinit var atClosure : () -> R

    override fun resumeWith(result: Result<Unit>) =
        throw result.exceptionOrNull() ?: IllegalArgumentException("No such at case for argument=[$at]")

    override fun <A> definedAt(f: (A) -> R, cast: (Any) -> A?): Boolean =
        cast(at)?.let { atClosure = { f(it) } } == Unit

    fun <L> mapAsRight() : Either<L, R> = atClosure().pure()

}