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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.amethyst.commons.util.deleteRecursivelyQuietly
import com.vitorpamplona.amethyst.commons.util.platformFileSystem
import com.vitorpamplona.cordn.appMultiDevice.CordnMigrationSnapshot
import com.vitorpamplona.cordn.client.CoordinatorConfig
import com.vitorpamplona.cordn.client.CordnBlobCipher
import com.vitorpamplona.cordn.client.CordnLocalState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import okio.FileSystem
import okio.Path

/**
 * Reading a handoff snapshot off the cordn stores on disk, and writing one back.
 *
 * What a snapshot carries and the order it is laid down in are cordn-kmp's
 * ([CordnLocalState], [CordnMigrationSnapshot.of], [CordnMigrationSnapshot.toLocalState]); this
 * only knows where Amethyst keeps the files. Shared by the app and `amy`, so the two cannot
 * disagree about what a migration carries.
 */
object CordnMigrationStores {
    /** Collects every group on disk for [configs], including those whose session failed to open today. */
    suspend fun read(
        root: Path,
        accountPubKey: HexKey,
        cipher: CordnBlobCipher,
        configs: List<CoordinatorConfig>,
        fileSystem: FileSystem = platformFileSystem,
    ): CordnMigrationSnapshot {
        val state = CordnLocalState.read(configs.map { it.pubKey }) { storesFor(root, accountPubKey, cipher, fileSystem, it) }
        return CordnMigrationSnapshot.of(accountPubKey, configs, state)
    }

    /**
     * Replaces this device's cordn tree with [snapshot]'s, returning the coordinator list to start.
     *
     * **Replaces, and is not a merge.** The old tree goes first, so a `gid` present in both cannot
     * end up half from each.
     */
    suspend fun write(
        root: Path,
        accountPubKey: HexKey,
        cipher: CordnBlobCipher,
        snapshot: CordnMigrationSnapshot,
        fileSystem: FileSystem = platformFileSystem,
    ): List<CoordinatorConfig> {
        require(snapshot.accountPubKey == accountPubKey) {
            "this migration belongs to a different account"
        }

        fileSystem.deleteRecursivelyQuietly(root / "cordn" / accountPubKey)
        CordnLocalState.write(snapshot.toLocalState()) { storesFor(root, accountPubKey, cipher, fileSystem, it) }
        return snapshot.coordinatorConfigs()
    }

    private fun storesFor(
        root: Path,
        accountPubKey: HexKey,
        cipher: CordnBlobCipher,
        fileSystem: FileSystem,
        coordinatorPubKey: HexKey,
    ): CordnLocalState.Stores {
        val dir = CordnStorageLayout.directoryFor(root, accountPubKey, coordinatorPubKey)
        return CordnLocalState.Stores(FileCordnGroupStore(dir, cipher, fileSystem), FileCordnKeyPackageStore(dir, cipher, fileSystem))
    }
}
