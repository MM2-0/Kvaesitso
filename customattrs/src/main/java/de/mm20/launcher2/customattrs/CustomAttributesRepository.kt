package de.mm20.launcher2.customattrs

import de.mm20.launcher2.search.data.Searchable
import kotlinx.coroutines.flow.Flow

interface CustomAttributesRepository {
    fun getCustomAttributes(searchable: Searchable, type: CustomAttributeType? = null): Flow<List<CustomAttribute>>
}