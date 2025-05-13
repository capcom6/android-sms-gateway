package me.capcom.smsgateway.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import me.capcom.smsgateway.databinding.FragmentWebhooksListBinding
import me.capcom.smsgateway.modules.webhooks.WebHooksService
import me.capcom.smsgateway.ui.adapters.WebhookAdapter
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
}
