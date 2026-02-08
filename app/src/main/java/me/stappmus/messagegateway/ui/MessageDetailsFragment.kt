package me.stappmus.messagegateway.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import me.stappmus.messagegateway.databinding.FragmentMessageDetailsBinding
import me.stappmus.messagegateway.modules.messages.vm.MessageDetailsViewModel
import me.stappmus.messagegateway.ui.adapters.MessageRecipientsAdapter
import org.koin.androidx.viewmodel.ext.android.viewModel

class MessageDetailsFragment : Fragment() {
    private val viewModel: MessageDetailsViewModel by viewModel()
    private var _binding: FragmentMessageDetailsBinding? = null
    private val binding get() = _binding!!

    private val id: String
        get() = requireNotNull(requireArguments().getString(ARG_ID)) { "id is null" }

    private val recipientsAdapter by lazy { MessageRecipientsAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentMessageDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerViewRecipients.adapter = recipientsAdapter
        binding.recyclerViewRecipients.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )

        viewModel.message.observe(viewLifecycleOwner) {
            binding.textMessageId.text = it.message.id
            binding.textMessage.text = it.message.content.toString()
            binding.textMessageState.text = it.state.name
            recipientsAdapter.submitList(it.recipients)
        }
        viewModel.get(id)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ID = "id"
        fun newInstance(id: String) =
            MessageDetailsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ID, id)
                }
            }
    }
}