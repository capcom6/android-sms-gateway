package me.stappmus.messagegateway.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import me.stappmus.messagegateway.R
import me.stappmus.messagegateway.databinding.FragmentWebhooksListBinding
import me.stappmus.messagegateway.modules.webhooks.WebHooksService
import me.stappmus.messagegateway.ui.adapters.WebhookAdapter
import org.koin.android.ext.android.inject

class WebhooksListFragment : Fragment() {
    private var _binding: FragmentWebhooksListBinding? = null
    private val binding get() = _binding!!

    private val webhookService: WebHooksService by inject()
    private val adapter: WebhookAdapter = WebhookAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebhooksListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadWebhooks()
    }

    private fun setupRecyclerView() {
        binding.webhookList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@WebhooksListFragment.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun loadWebhooks() {
        val webhooks = webhookService.select(null)
        adapter.submitList(webhooks)
        binding.emptyState.isVisible = webhooks.isEmpty()
        binding.webhookList.isVisible = webhooks.isNotEmpty()
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
}
