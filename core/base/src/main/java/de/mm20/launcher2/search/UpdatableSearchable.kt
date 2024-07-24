package de.mm20.launcher2.search

/**
 * Interface that can be implemented by [SavableSearchable]s to provide a way to update itself.
 * Consumers of [SavableSearchable]s can check if the [SavableSearchable] implements this interface
 * and decide to get an updated version of the [SavableSearchable] by calling [updatedSelf], which
 * returns an [UpdateResult] that contains either an up-to-date value or specifies unavailability.
 */
interface UpdatableSearchable<T : SavableSearchable> {
    val timestamp: Long
    val updatedSelf: (suspend (SavableSearchable) -> UpdateResult<T>)?
}

sealed class UpdateResult<out T> {
    data class Success<out T>(val result: T) : UpdateResult<T>()
    data class TemporarilyUnavailable<T>(val cause: Throwable? = null) : UpdateResult<T>()
    data class PermanentlyUnavailable<T>(val cause: Throwable? = null) : UpdateResult<T>()
}

fun <T>Result<T?>.asUpdateResult(): UpdateResult<T> {
    return if (isSuccess) {
        val refreshed = getOrNull()
        if (refreshed == null) {
            UpdateResult.PermanentlyUnavailable()
        } else {
            UpdateResult.Success(refreshed)
        }
    } else {
        UpdateResult.TemporarilyUnavailable(exceptionOrNull())
    }
}