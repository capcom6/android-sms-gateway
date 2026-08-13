package me.capcom.smsgateway.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.capcom.smsgateway.R
import me.capcom.smsgateway.databinding.FragmentIncomingMessagesListBinding
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import me.capcom.smsgateway.modules.incoming.vm.IncomingMessagesListViewModel
import me.capcom.smsgateway.ui.adapters.IncomingMessagesAdapter
import org.koin.androidx.viewmodel.ext.android.viewModel

class IncomingMessagesListFragment : Fragment() {
    private val viewModel: IncomingMessagesListViewModel by viewModel()
    private val adapter = IncomingMessagesAdapter()

    private var _binding: FragmentIncomingMessagesListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIncomingMessagesListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(scrollListener)
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )

        // Observe totals to update filter chip counts; hide zero-valued chips
        viewModel.totals.observe(viewLifecycleOwner) { stats ->
            stats?.let {
                binding.filterAll.text = getString(R.string.filter_all_count, it.total)
                binding.filterSms.text = getString(R.string.filter_sms_count, it.sms)
                binding.filterSms.visibility = if (it.sms > 0) View.VISIBLE else View.GONE
                binding.filterDataSms.text = getString(R.string.filter_data_sms_count, it.dataSms)
                binding.filterDataSms.visibility =
                    if (it.dataSms > 0) View.VISIBLE else View.GONE
                binding.filterMms.text = getString(R.string.filter_mms_count, it.mms)
                binding.filterMms.visibility = if (it.mms > 0) View.VISIBLE else View.GONE

                // If the active filter's chip was just hidden, reset to All
                if (viewModel.filter.value != null) {
                    val activeVisible = when (viewModel.filter.value) {
                        IncomingMessageType.SMS -> it.sms > 0
                        IncomingMessageType.DATA_SMS -> it.dataSms > 0
                        IncomingMessageType.MMS,
                        IncomingMessageType.MMS_DOWNLOADED -> it.mms > 0
                        else -> true
                    }
                    if (!activeVisible) viewModel.setFilter(null)
                }
            }
        }

        viewModel.messages.observe(viewLifecycleOwner) {
            val shouldScrollToTop = binding.recyclerView.computeVerticalScrollOffset() == 0
            adapter.submitList(it) {
                if (shouldScrollToTop) binding.recyclerView.scrollToPosition(0)
            }
        }

        // Filter chips setup
        binding.filterAll.isChecked = true

        viewModel.filter.observe(viewLifecycleOwner) { type ->
            when (type) {
                null -> { if (!binding.filterAll.isChecked) binding.filterAll.isChecked = true }
                IncomingMessageType.SMS -> { if (!binding.filterSms.isChecked) binding.filterSms.isChecked = true }
                IncomingMessageType.DATA_SMS -> { if (!binding.filterDataSms.isChecked) binding.filterDataSms.isChecked = true }
                IncomingMessageType.MMS,
                IncomingMessageType.MMS_DOWNLOADED -> { if (!binding.filterMms.isChecked) binding.filterMms.isChecked = true }
                else -> { if (!binding.filterAll.isChecked) binding.filterAll.isChecked = true }
            }
        }

        binding.filterChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.filterAll -> viewModel.setFilter(null)
                R.id.filterSms -> viewModel.setFilter(IncomingMessageType.SMS)
                R.id.filterDataSms -> viewModel.setFilter(IncomingMessageType.DATA_SMS)
                R.id.filterMms -> viewModel.setFilter(IncomingMessageType.MMS)
                else -> viewModel.setFilter(null)
            }
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

            if (dy <= 0 || adapter.itemCount == 0) return

            val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            val lastPos = manager.findLastVisibleItemPosition()
            if (lastPos >= 0 && lastPos == adapter.itemCount - 1) {
                viewModel.loadMore(adapter.itemCount)
            }
        }
    }

    companion object {
        fun newInstance() = IncomingMessagesListFragment()
    }
}
