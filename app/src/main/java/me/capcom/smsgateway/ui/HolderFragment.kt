package me.capcom.smsgateway.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import me.capcom.smsgateway.R
import me.capcom.smsgateway.databinding.FragmentHolderBinding

class HolderFragment : Fragment() {
    private var _binding: FragmentHolderBinding? = null
    private val binding get() = _binding!!
    private var isOutgoingSelected = true

    companion object {
        const val TAG_OUTGOING = "tab_outgoing"
        const val TAG_INCOMING = "tab_incoming"

        private const val KEY_OUTGOING_SELECTED = "outgoing_selected"

        fun newInstance() = HolderFragment()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        parentFragmentManager.commit {
            setPrimaryNavigationFragment(this@HolderFragment)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHolderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonOutgoing.setOnClickListener {
            selectOutgoing()
        }

        binding.buttonIncoming.setOnClickListener {
            selectIncoming()
        }

        if (savedInstanceState == null) {
            selectOutgoing()
        } else {
            isOutgoingSelected = savedInstanceState.getBoolean(KEY_OUTGOING_SELECTED, true)
            updateButtonStates()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_OUTGOING_SELECTED, isOutgoingSelected)
    }

    private fun updateButtonStates() {
        binding.buttonOutgoing.isEnabled = !isOutgoingSelected
        binding.buttonIncoming.isEnabled = isOutgoingSelected
    }

    private fun selectOutgoing() {
        isOutgoingSelected = true

        updateButtonStates()
        val outgoing = childFragmentManager.findFragmentByTag(TAG_OUTGOING)
            ?: MessagesListFragment.newInstance()
        val incoming = childFragmentManager.findFragmentByTag(TAG_INCOMING)

        childFragmentManager.commit {
            if (!outgoing.isAdded) add(R.id.rootLayout, outgoing, TAG_OUTGOING)
            incoming?.let { hide(it) }
            show(outgoing)
        }
    }

    private fun selectIncoming() {
        isOutgoingSelected = false

        updateButtonStates()
        val incoming = childFragmentManager.findFragmentByTag(TAG_INCOMING)
            ?: IncomingMessagesListFragment.newInstance()
        val outgoing = childFragmentManager.findFragmentByTag(TAG_OUTGOING)

        childFragmentManager.commit {
            if (!incoming.isAdded) add(R.id.rootLayout, incoming, TAG_INCOMING)
            outgoing?.let { hide(it) }
            show(incoming)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
