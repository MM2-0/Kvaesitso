package de.mm20.launcher2.files.providers

import de.mm20.launcher2.search.File
import de.mm20.launcher2.search.SavableSearchable
import de.mm20.launcher2.search.UpdatableSearchable
import de.mm20.launcher2.search.UpdateResult

class DeferredFile(
    cachedFile: File,
    override val timestamp: Long,
    override var updatedSelf: (suspend (SavableSearchable) -> UpdateResult<File>)? = null,
) : File by cachedFile, UpdatableSearchable<File>