package scg.kt.either

import java.util.Collections.*
import kotlin.coroutines.startCoroutine

private val lazyLeftUnit by lazy { Unit.asLeft() }

sealed interface Either<out L, out R>

@JvmInline
value class Left<L>(val value: L) : Either<L, Nothing>

@JvmInline
value class Right<L>(val value: L) : Either<Nothing, L>

fun <L> L.asLeft(): Left<L> = Left(this)

fun <R> R.pure(): Either<Nothing, R> = Right(this)

inline fun <L, R, T> Either<L, R>.mapWith(f : R.() -> T): Either<L, T> =
    this.map(f)

inline fun <L, R, T> Either<L, R>.map(f : (R) -> T): Either<L, T> =
    this.flatMap { f(it).pure() }

inline fun <L, R, T> Either<L, R>.leftMapWith(f : L.() -> T): Either<T, R> =
    this.leftMap(f)

inline fun <L, R, T> Either<L, R>.leftMap(f : (L) -> T): Either<T, R> =
    this.leftFlatMap { f(it).asLeft() }

inline fun <L, R, T> Either<L, R>.flatMapWith(f : R.() -> Either<L, T>): Either<L, T> =
    this.flatMap(f)

inline fun <L, R, T> Either<L, R>.flatMap(f : (R) -> Either<L, T>): Either<L, T> =
    when (this) {
        is Right<R> -> f(value)
        is Left<L> ->
            this
    }

inline fun <L, R, T> Either<L, R>.leftFlatMapWith(f : L.() -> Either<T, R>): Either<T, R> =
    this.leftFlatMap(f)

inline fun <L, R, T> Either<L, R>.leftFlatMap(f : (L) -> Either<T, R>): Either<T, R> =
    when (this) {
        is Right<R> -> this
        is Left<L> ->
            f(value)
    }

fun <L, R> Either<L, R>.swap(): Either<R, L> =
    when (this) {
        is Right<R> -> value.asLeft()
        is Left<L> ->
            value.pure()
    }

inline fun <L, R, T> Either<L, R>.fold(foldLeft : (L) -> T, foldRight : (R) -> T): T =
    when (this) {
        is Right<R> -> foldRight(value)
        is Left<L> ->
            foldLeft(value)
    }

fun <T> Either<T, T>.merge() : T =
    when (this) {
        is Right<T> -> value
        is Left<T> ->
            value
    }

inline fun <L, R> Either<L, R>.onLeft(action : (L) -> Unit): Either<L, R> =
    this.also { (it as? Left<L>)?.run { action(value) } }

inline fun <L, R> Either<L, R>.onRight(action : (R) -> Unit): Either<L, R> =
    this.also { (it as? Right<R>)?.run { action(value) } }

fun <L, R> tailrec(initial : L, f : (L) -> Either<L, R>): R {

    tailrec fun eval(next : L): R =
        when (val either = f(next)) {
            is Left<L> ->
                eval(either.value)
            is Right<R> ->
                either.value
        }

    return eval(initial)
}

fun <L, R> Either<L, R>.tailrec(f : (L) -> Either<L, R>): R {

    tailrec fun eval(next : L): R =
        when (val either = f(next)) {
            is Right<R> -> either.value
            is Left<L> ->
                eval(either.value)
        }

    return this.fold(::eval) { it }
}

operator fun <R> Either<*, R>.iterator() : Iterator<R> =
    when (this) {
        is Right<R> -> singleton(value).iterator()
        else ->
            emptyIterator()
    }

fun <R> Either<*, R>.toSet(): Set<R> =
    when (this) {
        is Right<R> -> setOf(value)
        else ->
            emptySet()
    }

fun <R> Either<*, R>.toList(): List<R> =
    when (this) {
        is Right<R> -> listOf(value)
        else ->
            emptyList()
    }

operator fun <L, R, T> Either<L, R>.plus(operand : Either<L, T>): Either<L, Pair<R, T>> =
    this.flatMap { r -> operand.map { r to it } }

fun <L, R> List<Either<L, R>>.sequence() : Either<L, List<R>> =
    this.fold(Right(mutableListOf())) { acc : Either<L, MutableList<R>>, next ->
        next.flatMap { r -> acc.map { it.apply { add(r) } } }
    }

fun <L, R> List<Either<L, R>>.unzip() : Pair<List<L>, List<R>> {

    val lefts = mutableListOf<L>()
    val rights = mutableListOf<R>()

    this.forEach {
        when (it) {
            is Right<R> ->
                rights.add(it.value)
            is Left<L> ->
                lefts.add(it.value)
        }
    }

    return lefts to rights
}

fun <L, R, T> ((R) -> T).lift() : (Either<L, R>) -> Either<L, T> =
    { it.map(this) }

fun <R> option(value : R?): Either<Unit, R> =
    value?.pure() ?: lazyLeftUnit

fun <R> catch(attempt : () -> R): Either<Throwable, R> =
    try {
        attempt().pure()
    } catch (throwable: Throwable) {
        throwable.asLeft()
    }

@Suppress("DANGEROUS_CHARACTERS")
infix fun <L, R> (() -> L).`?`(right : () -> R) : (Boolean) -> Either<L, R> = {
    when {
        it -> right().pure()
        else ->
            (invoke()).asLeft()
    }
}

fun <L, R, T> Either<L, R>.fx(comprehension: suspend ComprehensionScope<L, T>.(R) -> T): Either<L, T> =
    this.flatMap { r ->
        ComprehensionScopeImpl<L, T>()
            .also { suspend { comprehension(it, r) }.startCoroutine(it) }
            .yield()
    }

fun <L, R> fx(comprehension: Comprehension<L, R>): Either<L, R> =
    ComprehensionScopeImpl<L, R>()
        .also { comprehension.startCoroutine(it, it) }
        .yield()

fun <R> Either<Case, R>.recoverWith(scope : suspend RecoverScope<R>.() -> Unit): Either<Case, R> {
    return RecoverCaseImpl<R>(left ?: (return this)).also { scope.startCoroutine(it, it) }.recoverCase()
}

fun <L, R> Either<L, Any>.polyMap(scope : suspend AtScope<R>.() -> Unit): Either<L, R> =
    when (this) {
        is Right<Any> -> AtScopeImpl<R>(value).also { scope.startCoroutine(it, it) }.mapAsRight()
        is Left<L> ->
            this
    }

val <L> Either<L, *>.left : L?
    get() = (this as? Left<L>)?.value

val <R> Either<*, R>.right : R?
    get() = (this as? Right<R>)?.value

val <R> Either<Throwable, R>.result: Result<R>
    get() = this.fold(
        foldRight = { Result.success(it) },
        foldLeft = { Result.failure(it) },
    )

