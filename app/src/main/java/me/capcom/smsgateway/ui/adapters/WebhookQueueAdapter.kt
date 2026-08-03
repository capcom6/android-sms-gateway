package me.capcom.smsgateway.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.capcom.smsgateway.R
import me.capcom.smsgateway.databinding.ItemWebhookQueueBinding
import me.capcom.smsgateway.modules.webhooks.db.WebhookQueueEntity
import java.text.DateFormat
import java.util.Date

class WebhookQueueAdapter(
    private val onItemClickListener: OnItemClickListener<WebhookQueueEntity>
) :
    ListAdapter<WebhookQueueEntity, WebhookQueueAdapter.ViewHolder>(WebhookQueueDiffCallback()) {

    class ViewHolder(private val binding: ItemWebhookQueueBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: WebhookQueueEntity) {
            binding.apply {
                statusText.text = item.status.displayName
                urlText.text = item.url
                retryText.text = binding.root.context.getString(
                    R.string.webhook_queue_retry_count_format,
                    item.retryCount
                )
                createdAtText.text =
                    DateFormat.getDateTimeInstance().format(Date(item.createdAt))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemWebhookQueueBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        ).also { holder ->
            holder.itemView.setOnClickListener {
                val position = holder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClickListener.onItemClick(getItem(position))
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class WebhookQueueDiffCallback : DiffUtil.ItemCallback<WebhookQueueEntity>() {
        override fun areItemsTheSame(
            oldItem: WebhookQueueEntity,
            newItem: WebhookQueueEntity
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: WebhookQueueEntity,
            newItem: WebhookQueueEntity
        ): Boolean {
            return oldItem == newItem
        }
    }

    interface OnItemClickListener<T> {
        fun onItemClick(item: T)
    }
}
