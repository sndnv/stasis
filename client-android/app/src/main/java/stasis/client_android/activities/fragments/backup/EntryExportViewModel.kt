package stasis.client_android.activities.fragments.backup

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import stasis.client_android.lib.api.clients.Clients
import stasis.client_android.lib.collection.rules.SourceUri
import stasis.client_android.lib.encryption.Aes
import stasis.client_android.lib.model.DatasetMetadata
import stasis.client_android.lib.model.EntityMetadata
import stasis.client_android.lib.ops.recovery.EntityContent
import stasis.client_android.persistence.config.ConfigRepository
import stasis.client_android.providers.ProviderContext
import java.io.File
import java.nio.file.FileSystems
import javax.inject.Inject

@HiltViewModel
class EntryExportViewModel @Inject constructor(
    application: Application,
    private val providerContextFactory: ProviderContext.Factory
) : AndroidViewModel(application) {
    suspend fun export(entity: String, metadata: DatasetMetadata): File = withContext(Dispatchers.IO) {
        val preferences = ConfigRepository.getPreferences(getApplication())
        val providerContext = providerContextFactory.getOrCreate(preferences).required()
        val clients = Clients(api = providerContext.api, core = providerContext.core)

        val entityMetadata = metadata.require(entity = entity, clients = clients)
        require(entityMetadata is EntityMetadata.WithContent) { "Entity [$entity] has no content to export" }

        val scheme = SourceUri.scheme(entity)
            ?: throw IllegalArgumentException("Entity [$entity] is not a library entity")
        val kind = providerContext.libraryKinds.firstOrNull { it.scheme == scheme }
            ?: throw IllegalArgumentException("No library kind is registered for scheme [$scheme]")

        val bytes = EntityContent.pull(
            metadata = entityMetadata,
            entityKey = entity,
            deviceSecret = providerContext.credentials.deviceSecret.get(),
            clients = clients,
            decryptor = Aes,
            onPartProcessed = { }
        ).buffer().use { it.readByteArray() }

        val exported = kind.export(bytes)
        val name = EntryDisplay.displayName(entity, entityMetadata, FileSystems.getDefault())

        writeToDownloads(fileName = fileName(name, exported.extension), content = exported.bytes)
    }

    private fun writeToDownloads(fileName: String, content: ByteArray): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists()) {
            downloads.mkdirs()
        }

        val base = fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")

        var target = File(downloads, fileName)
        var attempt = 1
        while (target.exists()) {
            val candidate = if (extension.isEmpty()) "$base-$attempt" else "$base-$attempt.$extension"
            target = File(downloads, candidate)
            attempt += 1
        }

        target.outputStream().use { it.write(content) }
        return target
    }

    private fun fileName(name: String, extension: String): String {
        val sanitized = name.replace(IllegalFileNameChars, "_").trim().ifBlank { "export" }
        return "$sanitized.$extension"
    }

    companion object {
        private val IllegalFileNameChars: Regex = "[/\\\\:*?\"<>|]".toRegex()
    }
}
