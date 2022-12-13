package de.mm20.launcher2.services.tags.impl

import de.mm20.launcher2.services.tags.TagsService

internal class TagsServiceImpl(
): TagsService {
    override fun getTags(startsWith: String?): List<String> {
        TODO("Not yet implemented")
    }

    override fun renameTag(oldName: String, newName: String) {
        TODO("Not yet implemented")
    }

    override fun deleteTag(tag: String) {
        TODO("Not yet implemented")
    }

    override fun cloneTag(tag: String, newTag: String) {
        TODO("Not yet implemented")
    }
}