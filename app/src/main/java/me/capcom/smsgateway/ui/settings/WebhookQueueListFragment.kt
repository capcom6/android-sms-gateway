package me.capcom.smsgateway.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import me.capcom.smsgateway.R
import me.capcom.smsgateway.databinding.FragmentWebhookQueueListBinding
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueEntity
import me.capcom.smsgateway.modules.webhooks.db.WebhookStatus
import me.capcom.smsgateway.modules.webhooks.vm.WebhookQueueViewModel
import me.capcom.smsgateway.ui.adapters.WebhookQueueAdapter
import org.koin.androidx.viewmodel.ext.android.viewModel

class WebhookQueueListFragment : Fragment(),
    WebhookQueueAdapter.OnItemClickListener<WebhookQueueEntity> {
    private val viewModel: WebhookQueueViewModel by viewModel()
    private val adapter: WebhookQueueAdapter = WebhookQueueAdapter(this)

    private var _binding: FragmentWebhookQueueListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebhookQueueListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilterChips()
        binding.filterAll.isChecked = true

        viewModel.filteredEntries.observe(viewLifecycleOwner) { entries ->
            adapter.submitList(entries)
            val isEmpty = entries.isEmpty()
            binding.emptyState.isVisible = isEmpty
            binding.webhookQueueList.isVisible = !isEmpty
            if (isEmpty) {
                val filterActive = viewModel.filter.value != null
                binding.emptyFilterMessage.isVisible = filterActive
                binding.emptyStateMessage.isVisible = !filterActive
                if (filterActive) {
                    binding.emptyFilterMessage.text = getString(
                        R.string.webhook_queue_empty_filter,
                        requireNotNull(viewModel.filter.value).displayName
                    )
                }
            }
            viewLifecycleOwner.lifecycleScope.launch { viewModel.refreshStatistics() }
        }

        viewModel.queueStatistics.observe(viewLifecycleOwner) { stats ->
            if (stats != null) {
                binding.filterAll.text = getString(R.string.filter_all_count, stats.total)
                binding.filterPending.text = getString(R.string.filter_pending_count, stats.pending)
                binding.filterProcessing.text =
                    getString(R.string.filter_processing_count, stats.processing)
                binding.filterCompleted.text =
                    getString(R.string.filter_completed_count, stats.completed)
                binding.filterFailed.text = getString(R.string.filter_failed_count, stats.failed)
                binding.filterPermanentlyFailed.text = getString(
                    R.string.filter_permanently_failed_count,
                    stats.permanentlyFailed
                )

                binding.filterPending.visibility = if (stats.pending > 0) View.VISIBLE else View.GONE
                binding.filterProcessing.visibility =
                    if (stats.processing > 0) View.VISIBLE else View.GONE
                binding.filterCompleted.visibility =
                    if (stats.completed > 0) View.VISIBLE else View.GONE
                binding.filterFailed.visibility = if (stats.failed > 0) View.VISIBLE else View.GONE
                binding.filterPermanentlyFailed.visibility =
                    if (stats.permanentlyFailed > 0) View.VISIBLE else View.GONE

                if (viewModel.filter.value != null) {
                    val activeVisible = when (viewModel.filter.value) {
                        WebhookStatus.PENDING -> stats.pending > 0
                        WebhookStatus.PROCESSING -> stats.processing > 0
                        WebhookStatus.COMPLETED -> stats.completed > 0
                        WebhookStatus.FAILED -> stats.failed > 0
                        WebhookStatus.PERMANENTLY_FAILED -> stats.permanentlyFailed > 0
                        else -> true
                    }
                    if (!activeVisible) viewModel.setFilter(null)
                }
            }
        }

        viewModel.filter.observe(viewLifecycleOwner) { status ->
            when (status) {
                null -> {
                    if (!binding.filterAll.isChecked) binding.filterAll.isChecked = true
                }
                WebhookStatus.PENDING -> {
                    if (!binding.filterPending.isChecked) binding.filterPending.isChecked = true
                }
                WebhookStatus.PROCESSING -> {
                    if (!binding.filterProcessing.isChecked) binding.filterProcessing.isChecked = true
                }
                WebhookStatus.COMPLETED -> {
                    if (!binding.filterCompleted.isChecked) binding.filterCompleted.isChecked = true
                }
                WebhookStatus.FAILED -> {
                    if (!binding.filterFailed.isChecked) binding.filterFailed.isChecked = true
                }
                WebhookStatus.PERMANENTLY_FAILED -> {
                    if (!binding.filterPermanentlyFailed.isChecked) {
                        binding.filterPermanentlyFailed.isChecked = true
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.refreshStatistics()
        }
    }

    private fun setupFilterChips() {
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.filterAll -> viewModel.setFilter(null)
                R.id.filterPending -> viewModel.setFilter(WebhookStatus.PENDING)
                R.id.filterProcessing -> viewModel.setFilter(WebhookStatus.PROCESSING)
                R.id.filterCompleted -> viewModel.setFilter(WebhookStatus.COMPLETED)
                R.id.filterFailed -> viewModel.setFilter(WebhookStatus.FAILED)
                R.id.filterPermanentlyFailed -> viewModel.setFilter(WebhookStatus.PERMANENTLY_FAILED)
                else -> viewModel.setFilter(null)
            }
        }
    }

    private fun setupRecyclerView() {
        binding.webhookQueueList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WebhookQueueListFragment.adapter
            addItemDecoration(
                DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
            )
        }
    }

    override fun onItemClick(item: WebhookQueueEntity) {
        val containerId = (requireView().parent as? View)?.id
            ?.takeIf { it != View.NO_ID }
            ?: return
        parentFragmentManager.commit {
            replace(containerId, WebhookQueueDetailFragment.newInstance(item.id))
            addToBackStack(null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
