package me.stappmus.messagegateway.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.appbar.MaterialToolbar
import me.stappmus.messagegateway.R
import me.stappmus.messagegateway.databinding.FragmentLogsBinding
import me.stappmus.messagegateway.modules.logs.vm.LogsViewModel
import me.stappmus.messagegateway.ui.adapters.LogItemsAdapter
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * A fragment representing a list of Items.
 */
class LogsListFragment : Fragment() {
    private val viewModel: LogsViewModel by viewModel()
    private val adapter = LogItemsAdapter()

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {

        }

        viewModel.lastEntries.observe(this) {
            adapter.submitList(it) {
                _binding?.root?.scrollToPosition(0)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.adapter = adapter
        binding.root.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        configureBackButton(true)
    }

    override fun onPause() {
        configureBackButton(false)
        super.onPause()
    }

    private fun configureBackButton(enabled: Boolean) {
        val toolbar = activity?.findViewById<MaterialToolbar>(R.id.topBar) ?: return
        if (!enabled) {
            toolbar.navigationIcon = null
            toolbar.setNavigationOnClickListener(null)
            return
        }

        toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        toolbar.navigationContentDescription = "Back"
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    companion object {
        fun newInstance() =
            LogsListFragment().apply {
                arguments = Bundle().apply {

                }
            }
    }
}
