package de.mm20.launcher2.services.tags

interface TagsService {
    fun getTags(startsWith: String? = null): List<String>
    fun renameTag(oldName: String, newName: String)
    fun deleteTag(tag: String)
    fun cloneTag(tag: String, newTag: String)
}