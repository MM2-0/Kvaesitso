package de.mm20.launcher2.icons

import androidx.paging.PagingSource
import androidx.paging.PagingState
import de.mm20.launcher2.customattrs.CustomIconPackIcon
import de.mm20.launcher2.icons.transformations.LauncherIconTransformation
import de.mm20.launcher2.icons.transformations.apply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class IconPackPagingSource(
    private val iconPackManager: IconPackManager,
    private val iconPack: String,
    private val transformations: List<LauncherIconTransformation>
) : PagingSource<Int, CustomIconWithPreview>() {
    override fun getRefreshKey(state: PagingState<Int, CustomIconWithPreview>): Int? {
        return null
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CustomIconWithPreview> {
        val page = params.key ?: 0

        val icons = withContext(Dispatchers.IO) {
            iconPackManager.getIcons(iconPack, page, page + params.loadSize)
        }

        val customIcons = mutableListOf<CustomIconWithPreview>()
        withContext(Dispatchers.Default) {
            for (icon in icons) {
                val data = CustomIconPackIcon(iconPack, icon.componentName?.flattenToString() ?: continue)

                val ic = iconPackManager.getIcon(
                    iconPack,
                    icon.componentName
                ) ?: continue

                customIcons.add(
                    CustomIconWithPreview(
                        preview = transformations.apply(ic),
                        customIcon = data,
                    )
                )
            }
        }

        return LoadResult.Page(
            data = customIcons,
            prevKey = if (page > 0) page - 1 else null,
            nextKey = if (icons.size == params.loadSize) page + 1 else null
        )

    }

}