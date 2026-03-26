package me.capcom.smsgateway.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.capcom.smsgateway.R
import me.capcom.smsgateway.databinding.ItemIncomingMessageBinding
import me.capcom.smsgateway.modules.incoming.db.IncomingMessage
import me.capcom.smsgateway.modules.incoming.db.IncomingMessageType
import java.text.DateFormat
import java.util.Date

class IncomingMessagesAdapter :
    ListAdapter<IncomingMessage, IncomingMessagesAdapter.IncomingMessageViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IncomingMessageViewHolder {
        return IncomingMessageViewHolder(
            ItemIncomingMessageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: IncomingMessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class IncomingMessageViewHolder(
        private val binding: ItemIncomingMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: IncomingMessage) {
            binding.textViewSender.text = item.sender
            binding.textViewType.text = when (item.type) {
                IncomingMessageType.SMS -> binding.root.context.getString(R.string.incoming_type_sms)
                IncomingMessageType.DATA_SMS -> binding.root.context.getString(R.string.incoming_type_data_sms)
                IncomingMessageType.MMS,
                IncomingMessageType.MMS_DOWNLOADED -> binding.root.context.getString(R.string.incoming_type_mms)
            }
            binding.textViewDate.text =
                DateFormat.getDateTimeInstance().format(Date(item.createdAt))
            binding.textViewPreview.text = item.contentPreview
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<IncomingMessage>() {
        override fun areItemsTheSame(oldItem: IncomingMessage, newItem: IncomingMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: IncomingMessage,
            newItem: IncomingMessage
        ): Boolean {
            return oldItem == newItem
        }
    }
}
