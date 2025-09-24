package me.capcom.smsgateway.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.recyclerview.widget.DividerItemDecoration
import me.capcom.smsgateway.R
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.databinding.FragmentMessagesListBinding
import me.capcom.smsgateway.modules.messages.vm.MessagesListViewModel
import me.capcom.smsgateway.ui.adapters.MessagesAdapter
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
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )

        // Observe stats LiveData
        viewModel.totals.observe(viewLifecycleOwner) { stats ->
            stats?.let {
                binding.totalMessages.text = getString(R.string.total_messages, it.total)
                binding.pendingMessages.text = getString(R.string.pending_messages, it.pending)
                binding.sentMessages.text = getString(R.string.sent_messages, it.sent)
                binding.deliveredMessages.text =
                    getString(R.string.delivered_messages, it.delivered)
                binding.failedMessages.text = getString(R.string.failed_messages, it.failed)
            }
        }

        viewModel.messages.observe(viewLifecycleOwner) {
            val shouldScrollToTop = binding.recyclerView.computeVerticalScrollOffset() == 0
            messagesAdapter.submitList(it) {
                if (shouldScrollToTop) _binding?.recyclerView?.scrollToPosition(0)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() =
            MessagesListFragment()
    }

    override fun onItemClick(item: Message) {
        parentFragmentManager.commit {
            replace(R.id.rootLayout, MessageDetailsFragment.newInstance(item.id))
            addToBackStack(null)
        }
    }
}