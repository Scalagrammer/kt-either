package scg.kt.either

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.suspendCoroutine

typealias Case = Throwable

sealed interface RecoverScope<R> {
    fun <C : Case> definedAt(f : (C) -> R, cast : (Case) -> C?) : Boolean
}

suspend inline fun <reified C : Case, R> RecoverScope<R>.case(noinline f : (C) -> R) {
    when { definedAt(f) { it as? C } -> suspendCoroutine<Unit> { Unit } }
}

internal class RecoverCaseImpl<R>(private val case : Case) : RecoverScope<R>, Continuation<Unit> {

    override val context = EmptyCoroutineContext

    private lateinit var recoverClosure : () -> Either<Throwable, R>

    override fun resumeWith(result: Result<Unit>) {
        when (val exception = result.exceptionOrNull()) {
            null -> recoverClosure = { case.asLeft() }
            else -> recoverClosure = { exception.asLeft() }
        }
    }

    override fun <C : Case> definedAt(f: (C) -> R, cast: (Throwable) -> C?): Boolean =
        cast(case)?.let { recoverClosure = { f(it).pure() } } == Unit

    fun recoverCase() : Either<Case, R> =
        try {
            recoverClosure()
        } catch (failure: Throwable) {
            failure.apply { addSuppressed(case) }.asLeft()
        }
}