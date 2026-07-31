package com.yukile.foldershelf.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.yukile.foldershelf.data.model.ItemType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * DocumentTreeUtils
 *
 * Storage Access Framework (SAF) uzerinden alinan agac/tekil belge
 * URI'leri icin tekrarlanan islemleri (boyut/dosya sayisi hesaplama,
 * paylasim icin alt dosyalari toplama) tek yerde birlestirir.
 *
 * Onemli: Burada hicbir dosya KOPYALANMAZ, YENIDEN ADLANDIRILMAZ veya
 * TASINMAZ. Yalnizca mevcut SAF agacinda "salt okunur" gezinme (listFiles,
 * length, lastModified) yapilir; boylece "klasor yapisi ve dosya isimleri
 * degismesin" gereksinimi teknik olarak garanti altina alinir.
 */
object DocumentTreeUtils {

    data class TreeStats(
        val totalSizeBytes: Long,
        val fileCount: Int,
        val lastModified: Long
    )

    /**
     * Bir klasorun (alt klasorler dahil) toplam boyutunu, icerdigi dosya
     * sayisini ve en son degistirilme tarihini hesaplar. Cagiran coroutine
     * iptal edilirse (Job.cancel()) islem bir sonraki dugumde durur -
     * "buyuk klasorlerde islem iptal edilebilsin" gereksinimini karsilar.
     */
    suspend fun computeStats(context: Context, uri: Uri, type: ItemType): TreeStats {
        val root = resolveDocument(context, uri, type) ?: return TreeStats(0L, 0, 0L)

        var totalSize = 0L
        var fileCount = 0
        var lastModified = 0L

        suspend fun walk(doc: DocumentFile) {
            currentCoroutineContext().ensureActive()
            if (doc.isDirectory) {
                doc.listFiles().forEach { child -> walk(child) }
            } else {
                totalSize += doc.length()
                fileCount += 1
                val modified = doc.lastModified()
                if (modified > lastModified) lastModified = modified
            }
        }

        walk(root)
        return TreeStats(totalSize, fileCount, lastModified)
    }

    /**
     * Paylasim (share) icin bir klasordeki (alt klasorler dahil) tum
     * dosyalarin icerik URI'lerini toplar. Tekil dosya oglerinde tek
     * elemanli bir liste doner.
     */
    suspend fun collectFileUris(context: Context, uri: Uri, type: ItemType): List<Uri> {
        val result = mutableListOf<Uri>()
        val root = resolveDocument(context, uri, type) ?: return result

        suspend fun walk(doc: DocumentFile) {
            currentCoroutineContext().ensureActive()
            if (doc.isDirectory) {
                doc.listFiles().forEach { child -> walk(child) }
            } else {
                result.add(doc.uri)
            }
        }
        walk(root)
        return result
    }

    private fun resolveDocument(context: Context, uri: Uri, type: ItemType): DocumentFile? {
        return if (type == ItemType.FOLDER) {
            DocumentFile.fromTreeUri(context, uri)
        } else {
            DocumentFile.fromSingleUri(context, uri)
        }
    }
}
