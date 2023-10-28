package de.mm20.launcher2.backup

import java.io.File

interface Backupable {
    suspend fun backup(toDir: File)
    suspend fun restore(fromDir: File)
}