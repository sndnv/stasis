package stasis.client_android.activities.fragments.backup

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import stasis.client_android.sources.EntityPreview
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import javax.inject.Inject

@HiltViewModel
class EntryPreviewViewModel @Inject constructor(
    application: Application,
    private val providerContextFactory: ProviderContext.Factory
) : AndroidViewModel(application) {
    private val _state: MutableLiveData<PreviewState> = MutableLiveData(PreviewState.Loading)
    val state: LiveData<PreviewState> = _state

    private var started: Boolean = false

    fun load(entity: String, metadata: DatasetMetadata) {
        if (started) return
        started = true

        val preferences = ConfigRepository.getPreferences(getApplication())
        val providerContext = providerContextFactory.getOrCreate(preferences).required()

        viewModelScope.launch {
            _state.value = runCatching { resolve(entity, metadata, providerContext) }
                .getOrElse { PreviewState.Failed(it) }
        }
    }

    private suspend fun resolve(
        entity: String,
        metadata: DatasetMetadata,
        providerContext: ProviderContext
    ): PreviewState = withContext(Dispatchers.IO) {
        val clients = Clients(api = providerContext.api, core = providerContext.core)

        val entityMetadata = metadata.require(entity = entity, clients = clients)
        if (entityMetadata !is EntityMetadata.WithContent) {
            return@withContext PreviewState.Unsupported(size = null)
        }

        if (entityMetadata.size > PreviewMaxBytes) {
            return@withContext PreviewState.TooLarge(size = entityMetadata.size)
        }

        val bytes = EntityContent.pull(
            metadata = entityMetadata,
            entityKey = entity,
            deviceSecret = providerContext.credentials.deviceSecret.get(),
            clients = clients,
            decryptor = Aes,
            onPartProcessed = { }
        ).buffer().use { it.readByteArray() }

        when (val scheme = SourceUri.scheme(entity)) {
            null -> renderFilesystem(bytes, entityMetadata.size)
            else -> when (val kind = providerContext.libraryKinds.firstOrNull { it.scheme == scheme }) {
                null -> PreviewState.Unsupported(size = entityMetadata.size)
                else -> PreviewState.Library(kind.preview(bytes))
            }
        }
    }

    private fun renderFilesystem(bytes: ByteArray, size: Long): PreviewState {
        decodeImage(bytes)?.let { return PreviewState.Image(it) }
        decodeText(bytes)?.let { return PreviewState.Text(it) }
        return PreviewState.Unsupported(size = size)
    }

    private fun decodeImage(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > MaxImageDimension || height / sampleSize > MaxImageDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun decodeText(bytes: ByteArray): String? =
        try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString().take(TextCapChars)
        } catch (e: CharacterCodingException) {
            null
        }

    sealed class PreviewState {
        object Loading : PreviewState()
        data class Library(val preview: EntityPreview) : PreviewState()
        data class Text(val content: String) : PreviewState()
        data class Image(val bitmap: Bitmap) : PreviewState()
        data class TooLarge(val size: Long) : PreviewState()
        data class Unsupported(val size: Long?) : PreviewState()
        data class Failed(val error: Throwable) : PreviewState()
    }

    companion object {
        private const val PreviewMaxBytes: Long = 10L * 1024 * 1024
        private const val TextCapChars: Int = 200_000
        private const val MaxImageDimension: Int = 2048
    }
}
