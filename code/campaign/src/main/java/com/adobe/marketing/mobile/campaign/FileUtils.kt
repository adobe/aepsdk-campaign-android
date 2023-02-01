/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
 */

package com.adobe.marketing.mobile.campaign

import android.database.sqlite.SQLiteDatabase
import com.adobe.marketing.mobile.campaign.CampaignConstants.LOG_TAG
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.services.ServiceProvider
import com.adobe.marketing.mobile.util.StringUtils
import java.io.*
import java.util.zip.ZipInputStream

internal object FileUtils {
    private const val TAG = "FileUtils"
    private const val MAX_BUFFER_SIZE = 4096

    /**
     * Helper method to delete the obsolete ACPCampaign 1.x hit database
     */
    @JvmStatic
    fun deleteDatabaseFromCacheDir(fileName: String?): Boolean {
        return try {
            val cacheDir = ServiceProvider.getInstance().deviceInfoService.applicationCacheDir
            if (cacheDir == null || StringUtils.isNullOrEmpty(fileName)) {
                return false
            }
            val databaseFile = File(cacheDir, fileName)
            SQLiteDatabase.deleteDatabase(databaseFile)
        } catch (e: java.lang.Exception) {
            Log.debug(LOG_TAG, TAG, "Failed to delete (%s) in cache folder, exception occurred: (%s)", fileName, e.localizedMessage)
            false
        }
    }

    /**
     * Reads the content of [inputStream] into [file].
     *
     * @param file the file whose contents need to be created/updated read from the [inputStream]
     * @param inputStream the [InputStream] from which the contents should be read
     * @param append if the contents of [inputStream] should be appended to the contents of [file]
     * @return true if the contents of the input stream were added to the file,
     *         false otherwise
     */
    @JvmStatic
    fun readInputStreamIntoFile(file: File?, inputStream: InputStream, append: Boolean): Boolean {
        return try {
            FileOutputStream(file, append).use { outputStream ->
                inputStream.copyTo(outputStream, MAX_BUFFER_SIZE)
            }

            true
        } catch (e: Exception) {
            Log.debug(
                LOG_TAG,
                TAG,
                "Unexpected exception while attempting to write to file: ${file?.path} ($e)"
            )
            false
        }
    }

    /**
     * Extracts the zip file to an output directory.
     *
     * @param zipFile the zip file that needs to be extracted
     * @param outputDirectoryPath the destination for the extracted [zipFile] contents
     * @return true if the zip file has been successfully extracted
     *         false otherwise
     */
    @JvmStatic
    fun extractFromZip(zipFile: File?, outputDirectoryPath: String): Boolean {
        if (zipFile == null) return false

        val folder = File(outputDirectoryPath)
        if (!folder.exists() && !folder.mkdir()) {
            Log.debug(
                LOG_TAG,
                TAG,
                "Could not create the output directory $outputDirectoryPath"
            )
            return false
        }

        var extractedSuccessfully = true
        try {
            ZipInputStream(FileInputStream(zipFile)).use { zipInputStream ->
                // get the zipped file list entry
                var ze = zipInputStream.nextEntry
                val outputFolderCanonicalPath = folder.canonicalPath
                if (ze == null) {
                    // Invalid zip file!
                    Log.debug(
                        LOG_TAG, TAG, "Zip file was invalid"
                    )
                    return false
                }
                var entryProcessedSuccessfully = true
                while (ze != null && entryProcessedSuccessfully) {
                    val fileName = ze.name
                    val newZipEntryFile = File(outputDirectoryPath + File.separator + fileName)
                    if (!newZipEntryFile.canonicalPath.startsWith(outputFolderCanonicalPath)) {
                        Log.debug(
                            LOG_TAG,
                            TAG,
                            "The zip file contained an invalid path. Verify that your zip file is formatted correctly and has not been tampered with."
                        )
                        return false
                    }
                    entryProcessedSuccessfully = if (ze.isDirectory) {
                        // handle directory
                        (newZipEntryFile.exists() || newZipEntryFile.mkdirs())
                    } else {
                        // handle file
                        val parentFolder = newZipEntryFile.parentFile
                        if (parentFolder != null && (parentFolder.exists() || parentFolder.mkdirs())) {
                            readInputStreamIntoFile(newZipEntryFile, zipInputStream, false)
                        } else {
                            Log.debug(
                                LOG_TAG,
                                TAG,
                                "Could not extract the file ${newZipEntryFile.absolutePath}"
                            )
                            return false
                        }
                    }
                    extractedSuccessfully = extractedSuccessfully && entryProcessedSuccessfully

                    zipInputStream.closeEntry()
                    ze = zipInputStream.nextEntry
                }
                zipInputStream.closeEntry()
            }
        } catch (ex: Exception) {
            Log.debug(
                LOG_TAG, TAG, "Extraction failed - $ex"
            )
            extractedSuccessfully = false
        }

        return extractedSuccessfully
    }

    @JvmStatic
    @Throws(SecurityException::class)
    fun deleteFile(fileToDelete: File?, recursive: Boolean): Boolean {
        if (fileToDelete == null) {
            return false
        }
        return if (recursive) fileToDelete.deleteRecursively() else fileToDelete.delete()
    }
}
