package me.capcom.smsgateway.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.databinding.ItemMessageBinding
import me.capcom.smsgateway.ui.styles.color

class MessageRecipientsAdapter :
    ListAdapter<MessageRecipient, MessageRecipientsAdapter.RecipientViewHolder>(
        RecipientsDiffCallback()
    ) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecipientViewHolder {
        return RecipientViewHolder.create(parent)
    }

    override fun onBindViewHolder(holder: RecipientViewHolder, position: Int) {
        val message = getItem(position)

        holder.bind(message)
    }

    class RecipientViewHolder(private val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(recipient: MessageRecipient) {
            binding.textViewId.text = recipient.phoneNumber
            binding.textViewState.text = recipient.state.name
            binding.textViewDate.text = recipient.error
            binding.imageViewState.setColorFilter(recipient.state.color)
        }

        companion object {
            fun create(parent: ViewGroup): RecipientViewHolder {
                return RecipientViewHolder(
                    ItemMessageBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                )
            }
        }
    }

    class RecipientsDiffCallback :
        androidx.recyclerview.widget.DiffUtil.ItemCallback<MessageRecipient>() {
        override fun areItemsTheSame(
            oldItem: MessageRecipient,
            newItem: MessageRecipient
        ): Boolean {
            return oldItem.phoneNumber == newItem.phoneNumber
        }

        override fun areContentsTheSame(
            oldItem: MessageRecipient,
            newItem: MessageRecipient
        ): Boolean {
            return oldItem == newItem
        }
    }
}