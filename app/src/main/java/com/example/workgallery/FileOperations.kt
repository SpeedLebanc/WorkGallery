package com.example.workgallery

// Android content and data management. Android 内容和数据管理。
import android.content.ContentValues // Used to store sets of values that the ContentResolver can process. 用于存储 ContentResolver 可以处理的值集合。
import android.content.Context // The gateway to application resources and system services. 通往应用程序资源和系统服务的门户。
import android.content.Intent // Used to receive and process data from other apps. 用于接收和处理来自其他应用的数据。
import android.graphics.Bitmap // Represents a memory-resident image for thumbnails. 代表内存中的缩略图图像。
import android.media.MediaMetadataRetriever // Used to extract frames and metadata from video files. 用于从视频文件中提取帧和元数据。
import android.net.Uri // A string representing the location of a resource (file or content). 代表资源（文件或内容）位置的字符串。
import android.os.Environment // Provides access to environment variables like system paths. 提供对环境变量（如系统路径）的访问。
import android.provider.MediaStore // The system-defined database for all media files on the device. 设备上所有媒体文件的系统定义数据库。

// Backward compatibility and storage abstraction. 向后兼容性和存储抽象。
import androidx.core.content.IntentCompat // Helper for retrieving data from Intents across different API levels. 用于在不同 API 级别从 Intent 中检索数据的辅助工具。
import androidx.documentfile.provider.DocumentFile // Provides a standard way to manage files in SAF directories. 提供在 SAF 目录中管理文件的标准方法。

// Standard Java I/O for file handling. 用于文件处理的标准 Java I/O。
import java.io.File // Represents a file or directory path. 代表文件或目录路径。
import java.io.FileOutputStream // Used to write raw bytes into a file. 用于将原始字节写入文件。

/**
 * Handle files shared from the system gallery or other apps via "Share/Move".
 * 处理通过“分享/移动”从系统相册或其他应用传来的文件。
 */
fun handleSharedFiles(context: Context, intent: Intent?) {
    // Return immediately if the intent is null. 如果 Intent 为空则立即返回。
    if (intent == null) return

    // Create a specific directory for received files. 为接收到的文件创建一个特定目录。
    val receiveDir = File(context.getExternalFilesDir(null), "WorkAlbums/SystemGallery")
    if (!receiveDir.exists()) receiveDir.mkdirs()

    try {
        // Handle a single file share. 处理单个文件分享。
        if (intent.action == Intent.ACTION_SEND) {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            uri?.let { saveUriToFile(context, it, receiveDir) }
        }
        // Handle multiple files shared at once. 处理一次分享的多个文件。
        else if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            uris?.forEach { uri -> saveUriToFile(context, uri, receiveDir) }
        }
    } catch (e: Exception) {
        // Log any errors during the sharing process. 记录分享过程中的任何错误。
        e.printStackTrace()
    }
}

/**
 * Copy data from a content URI to a local file in the app's directory.
 * 将内容 URI 中的数据复制到应用目录中的本地文件。
 */
private fun saveUriToFile(context: Context, uri: Uri, targetDir: File) {
    // Determine file extension from the MIME type. 从 MIME 类型确定文件扩展名。
    val extension = context.contentResolver.getType(uri)?.split("/")?.last() ?: "jpg"
    // Create a new destination file with a unique timestamp. 使用唯一的时间戳创建一个新的目标文件。
    val destFile = File(targetDir, "Received_${System.currentTimeMillis()}.$extension")

    // Open an input stream from the URI and an output stream to the file. 从 URI 打开输入流，向文件打开输出流。
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(destFile).use { output ->
            // Copy the data block by block. 逐块复制数据。
            input.copyTo(output)
        }
    }
}

/**
 * Move local files into the public system gallery using MediaStore.
 * 使用 MediaStore 将本地文件移动到公共系统相册。
 */
fun moveFilesToSystemGallery(context: Context, files: Set<File>) {
    files.forEach { file ->
        // Set the correct MIME type based on extension. 根据扩展名设置正确的 MIME 类型。
        val mimeType = if (file.extension == "mp4") "video/mp4" else "image/jpeg"

        // Prepare metadata for the MediaStore entry. 为 MediaStore 条目准备元数据。
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            // Save inside the DCIM/WorkGallery folder. 保存在 DCIM/WorkGallery 文件夹内。
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/WorkGallery")
        }

        // Select the appropriate collection (Images or Video). 选择合适的集合（图像或视频）。
        val collection = if (mimeType.startsWith("video")) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        // Insert the record and copy the file data. 插入记录并复制文件数据。
        context.contentResolver.insert(collection, values)?.let { dest ->
            context.contentResolver.openOutputStream(dest)?.use { out ->
                file.inputStream().copyTo(out)
            }
            // Delete the original file from private storage after successful move. 成功移动后，从私有存储中删除原始文件。
            file.delete()
        }
    }
}

/**
 * Move files to any folder selected by the user via the Storage Access Framework (SAF).
 * 通过存储访问框架 (SAF) 将文件移动到用户选择的任意文件夹。
 */
fun moveFilesToSafFolder(context: Context, files: Set<File>, treeUri: Uri) {
    // Get the directory reference from the tree URI. 从树 URI 获取目录引用。
    val documentTree = DocumentFile.fromTreeUri(context, treeUri) ?: return

    files.forEach { file ->
        val mimeType = if (file.extension == "mp4") "video/mp4" else "image/jpeg"
        // Create a new empty file in the target directory. 在目标目录中创建一个新的空文件。
        val newDoc = documentTree.createFile(mimeType, file.name)

        newDoc?.uri?.let { destUri ->
            // Copy the content to the SAF destination. 将内容复制到 SAF 目标位置。
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                file.inputStream().copyTo(out)
            }
            // Delete source file. 删除源文件。
            file.delete()
        }
    }
}

/**
 * Extract a thumbnail bitmap from a video file at a specific time.
 * 在特定时间点从视频文件中提取缩略图位图。
 */
fun getVideoThumbnail(path: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        // Set the source video path. 设置源视频路径。
        retriever.setDataSource(path)
        // Extract a frame at the 1-second mark (1,000,000 microseconds). 在 1 秒标记处（1,000,000 微秒）提取一帧。
        retriever.getFrameAtTime(1000000)
    } catch (e: Exception) {
        null
    } finally {
        // Always release the retriever resources. 务必释放检索器资源。
        retriever.release()
    }
}