package me.capcom.smsgateway.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.capcom.smsgateway.R
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.databinding.FragmentMessagesListBinding
import me.capcom.smsgateway.modules.messages.vm.MessagesListViewModel
import me.capcom.smsgateway.ui.adapters.MessagesAdapter
import me.capcom.smsgateway.domain.ProcessingState
import org.koin.androidx.viewmodel.ext.android.viewModel


class MessagesListFragment : Fragment(), MessagesAdapter.OnItemClickListener<Message> {

    private val viewModel: MessagesListViewModel by viewModel()
    private val messagesAdapter = MessagesAdapter(this)
    private var _binding: FragmentMessagesListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentMessagesListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.adapter = messagesAdapter
        binding.recyclerView.addOnScrollListener(scrollListener)
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )

        // Observe stats LiveData to update filter chip counts; hide zero-valued chips
        viewModel.totals.observe(viewLifecycleOwner) { stats ->
            stats?.let {
                binding.filterAll.text = getString(R.string.filter_all_count, it.total)
                binding.filterPending.text = getString(R.string.filter_pending_count, it.pending)
                binding.filterPending.visibility = if (it.pending > 0) View.VISIBLE else View.GONE
                binding.filterSent.text = getString(R.string.filter_sent_count, it.sent)
                binding.filterSent.visibility = if (it.sent > 0) View.VISIBLE else View.GONE
                binding.filterDelivered.text =
                    getString(R.string.filter_delivered_count, it.delivered)
                binding.filterDelivered.visibility =
                    if (it.delivered > 0) View.VISIBLE else View.GONE
                binding.filterFailed.text = getString(R.string.filter_failed_count, it.failed)
                binding.filterFailed.visibility = if (it.failed > 0) View.VISIBLE else View.GONE
                binding.filterCancelled.text =
                    getString(R.string.filter_cancelled_count, it.cancelled)
                binding.filterCancelled.visibility =
                    if (it.cancelled > 0) View.VISIBLE else View.GONE

                // If the active filter's chip was just hidden, reset to All
                if (viewModel.filter.value != null) {
                    val activeVisible = when (viewModel.filter.value) {
                        ProcessingState.Pending -> it.pending > 0
                        ProcessingState.Sent -> it.sent > 0
                        ProcessingState.Delivered -> it.delivered > 0
                        ProcessingState.Failed -> it.failed > 0
                        ProcessingState.Cancelled -> it.cancelled > 0
                        else -> true
                    }
                    if (!activeVisible) viewModel.setFilter(null)
                }
            }
        }

        viewModel.messages.observe(viewLifecycleOwner) {
            val shouldScrollToTop = _binding?.recyclerView?.computeVerticalScrollOffset() == 0
            messagesAdapter.submitList(it) {
                if (shouldScrollToTop) _binding?.recyclerView?.scrollToPosition(0)
            }
        }

        // Filter chips setup
        binding.filterAll.isChecked = true

        viewModel.filter.observe(viewLifecycleOwner) { state ->
            when (state) {
                null -> { if (!binding.filterAll.isChecked) binding.filterAll.isChecked = true }
                ProcessingState.Pending -> { if (!binding.filterPending.isChecked) binding.filterPending.isChecked = true }
                ProcessingState.Sent -> { if (!binding.filterSent.isChecked) binding.filterSent.isChecked = true }
                ProcessingState.Delivered -> { if (!binding.filterDelivered.isChecked) binding.filterDelivered.isChecked = true }
                ProcessingState.Failed -> { if (!binding.filterFailed.isChecked) binding.filterFailed.isChecked = true }
                ProcessingState.Cancelled -> { if (!binding.filterCancelled.isChecked) binding.filterCancelled.isChecked = true }
                else -> { if (!binding.filterAll.isChecked) binding.filterAll.isChecked = true }
            }
        }

        binding.filterChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.filterAll -> viewModel.setFilter(null)
                R.id.filterPending -> viewModel.setFilter(ProcessingState.Pending)
                R.id.filterSent -> viewModel.setFilter(ProcessingState.Sent)
                R.id.filterDelivered -> viewModel.setFilter(ProcessingState.Delivered)
                R.id.filterFailed -> viewModel.setFilter(ProcessingState.Failed)
                R.id.filterCancelled -> viewModel.setFilter(ProcessingState.Cancelled)
                else -> viewModel.setFilter(null)
            }
        }
    }

    override fun onItemClick(item: Message) {
        parentFragmentManager.commit {
            replace(R.id.rootLayout, MessageDetailsFragment.newInstance(item.id))
            addToBackStack(null)
        }
    }

    override fun onDestroyView() {
        binding.recyclerView.removeOnScrollListener(scrollListener)
        super.onDestroyView()
        _binding = null
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            val linearLayoutManager = recyclerView.layoutManager as? LinearLayoutManager
            linearLayoutManager?.findLastVisibleItemPosition()?.let {
                if (it == messagesAdapter.itemCount - 1) viewModel.loadMore(messagesAdapter.itemCount)
            }
        }
    }

    companion object {
        fun newInstance() =
            MessagesListFragment()
    }
}