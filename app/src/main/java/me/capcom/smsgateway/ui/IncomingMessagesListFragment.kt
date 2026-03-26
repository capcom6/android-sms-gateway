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

        viewModel.totals.observe(viewLifecycleOwner) { stats ->
            stats?.let {
                binding.totalIncomingMessages.text = getString(R.string.total_messages, it.total)
                binding.smsIncomingMessages.text = getString(R.string.incoming_sms_messages, it.sms)
                binding.dataIncomingMessages.text =
                    getString(R.string.incoming_data_messages, it.dataSms)
                binding.mmsIncomingMessages.text = getString(R.string.incoming_mms_messages, it.mms)
            }
        }

        viewModel.messages.observe(viewLifecycleOwner) {
            val shouldScrollToTop = binding.recyclerView.computeVerticalScrollOffset() == 0
            adapter.submitList(it) {
                if (shouldScrollToTop) binding.recyclerView.scrollToPosition(0)
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
