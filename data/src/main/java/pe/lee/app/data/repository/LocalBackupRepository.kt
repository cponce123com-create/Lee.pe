package pe.lee.app.data.repository

import pe.lee.app.data.local.BookEntity
import pe.lee.app.data.local.LocalBackupDao
import pe.lee.app.data.local.LocalBackupMergeReport
import pe.lee.app.data.local.LocalBackupSnapshot

class LocalBackupRepository(private val dao: LocalBackupDao) {
    suspend fun snapshot(): LocalBackupSnapshot = dao.snapshot()
    suspend fun allBooks(): List<BookEntity> = dao.allBooks()
    suspend fun merge(snapshot: LocalBackupSnapshot): LocalBackupMergeReport = dao.merge(snapshot)
}
