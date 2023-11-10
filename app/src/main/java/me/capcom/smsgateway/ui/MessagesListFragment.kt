package me.capcom.smsgateway.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import me.capcom.smsgateway.databinding.FragmentMessagesListBinding
import me.capcom.smsgateway.ui.adapters.MessagesAdapter
import me.capcom.smsgateway.ui.vm.MessagesListViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MessagesListFragment : Fragment() {

    private val viewModel: MessagesListViewModel by viewModel()
    private val messagesAdapter = MessagesAdapter()
    private var _binding: FragmentMessagesListBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.messages.observe(this) {
            messagesAdapter.submitList(it)
        }
    }

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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() =
            MessagesListFragment()
    }
}