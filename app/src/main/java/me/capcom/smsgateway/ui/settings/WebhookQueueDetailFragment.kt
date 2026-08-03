package me.capcom.smsgateway.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.capcom.smsgateway.R
import me.capcom.smsgateway.databinding.FragmentWebhookQueueDetailBinding
import me.capcom.smsgateway.modules.webhooks.WebhookPayloadStorage
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueDao
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueEntity
import org.koin.android.ext.android.inject
import java.text.DateFormat
import java.util.Date

class WebhookQueueDetailFragment : Fragment() {
    private val dao: WebhookQueueDao by inject()
    private val payloadStorage: WebhookPayloadStorage by inject()

    private var _binding: FragmentWebhookQueueDetailBinding? = null
    private val binding get() = _binding!!

    private val webhookId: String
        get() = requireNotNull(requireArguments().getString(ARG_ID)) { "id is null" }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebhookQueueDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            val entity = findEntity(webhookId)
            val currentBinding = _binding ?: return@launch
            if (entity == null) {
                currentBinding.detailLayout.isVisible = false
                currentBinding.notFoundLayout.isVisible = true
                return@launch
            }
            bindEntity(currentBinding, entity)
        }
    }

    private suspend fun bindEntity(
        binding: FragmentWebhookQueueDetailBinding,
        entity: WebhookQueueEntity
    ) {
        binding.apply {
            textWebhookId.text = getString(R.string.webhook_id_format, entity.id)
            textUrl.text = entity.url
            textStatus.text = entity.status.displayName
            textRetryCount.text = getString(R.string.webhook_queue_retry_count_format, entity.retryCount)
            textLastError.text = entity.lastError ?: getString(R.string.webhook_queue_no_error)
            textCreatedAt.text = formatTimestamp(entity.createdAt)
            textNextAttempt.text = formatTimestamp(entity.nextAttempt)
        }

        val payload = readPayload(entity.payload)
        val currentBinding = _binding ?: return
        currentBinding.textPayload.text =
            payload ?: getString(R.string.webhook_queue_payload_unavailable)
    }

    private suspend fun findEntity(id: String): WebhookQueueEntity? = withContext(Dispatchers.IO) {
        try {
            dao.getById(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Row may be deleted by cleanupOldEntries between list render and detail open.
            null
        }
    }

    private suspend fun readPayload(payloadRef: String): String? = withContext(Dispatchers.IO) {
        try {
            payloadStorage.read(payloadRef)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Payload file may already be deleted for terminal statuses.
            null
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return DateFormat.getDateTimeInstance().format(Date(timestamp))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ID = "id"
        fun newInstance(id: String) =
            WebhookQueueDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ID, id)
                }
            }
    }
}
