package com.kavach.guard

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/** Tiny provider so the evidence PDF can be shared to WhatsApp / email without the androidx FileProvider. */
class EvidenceProvider : ContentProvider() {
    override fun onCreate() = true

    private fun resolve(uri: Uri): File? {
        val dir = EvidencePack.dir(context ?: return null).canonicalFile
        val f = File(dir, uri.lastPathSegment ?: return null).canonicalFile
        return if (f.parentFile == dir && f.exists()) f else null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(resolve(uri) ?: throw FileNotFoundException(), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor? {
        val f = resolve(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols).apply {
            addRow(cols.map { if (it == OpenableColumns.DISPLAY_NAME) f.name else f.length() }.toTypedArray())
        }
    }

    override fun getType(uri: Uri) = "application/pdf"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0
    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
}
