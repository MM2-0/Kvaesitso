/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.reflect.KClass

/**
 * The [ViewModel] of [modelClass] scoped to the nearest ViewModel store owner (the screen or
 * dialog), built by [factory] the first time. Android and Desktop use lifecycle's `viewModel()`;
 * iOS, where that artifact is not published, keeps it for as long as the composition does.
 */
@Composable
expect fun <VM : ViewModel> rememberViewModel(
    modelClass: KClass<VM>,
    key: String?,
    factory: () -> VM,
): VM

/** [rememberViewModel] built through an existing [ViewModelProvider.Factory]. */
@Composable
expect fun <VM : ViewModel> rememberViewModel(
    modelClass: KClass<VM>,
    key: String?,
    factory: ViewModelProvider.Factory,
): VM

@Composable
inline fun <reified VM : ViewModel> rememberViewModel(
    key: String? = null,
    factory: ViewModelProvider.Factory,
): VM = rememberViewModel(VM::class, key, factory)

@Composable
inline fun <reified VM : ViewModel> rememberViewModel(
    key: String? = null,
    noinline factory: () -> VM,
): VM = rememberViewModel(VM::class, key, factory)

/**
 * One [ViewModelProvider.Factory] per call site for the lambda overloads of [rememberViewModel],
 * instead of a new object on every recomposition. It calls the latest [factory], which only runs
 * when the store has no view model yet.
 */
@Composable
@PublishedApi
internal fun <VM : ViewModel> rememberLambdaFactory(factory: () -> VM): ViewModelProvider.Factory {
    val currentFactory by rememberUpdatedState(factory)
    return remember {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: KClass<T>,
                extras: CreationExtras,
            ): T = currentFactory() as T
        }
    }
}
